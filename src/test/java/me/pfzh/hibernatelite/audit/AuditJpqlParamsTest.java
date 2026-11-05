package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariants of the JPQL parameter binding.
 *
 * <p>Source being analysed: {@code query/JpqlExecutor.page()}</p>
 *
 * <pre>
 * public &lt;T&gt; Page&lt;T&gt; page(String jpql, String countJpql, ..., Map params, PageRequest request) {
 *     List&lt;T&gt; raw = ...bindParams(query, params)...;
 *     long total = count(countJpql, params);       // &lt;-- same params map, different JPQL
 * }
 * private static void bindParams(Query q, Map params) {
 *     for (var e : params.entrySet()) q.setParameter(e.getKey(), e.getValue());
 * }
 * </pre>
 *
 * <p>{@code setParameter} throws {@code IllegalArgumentException} when the JPQL does
 * not declare that name. The count query therefore must declare <i>exactly</i> the same
 * set of parameter names as the page query - something the API never states and that
 * is easy to violate (e.g. a count query that only needs a subset of the filters).</p>
 */
class AuditJpqlParamsTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-jpqlp");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25, "ACTIVE"),
                new TestUser("Bob", 17, "ACTIVE"),
                new TestUser("Charlie", 30, "INACTIVE"),
                new TestUser("Dave", 15, "INACTIVE"),
                new TestUser("Eve", 22, "ACTIVE")));
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    @Test
    void page_withIdenticalParametersWorks() {
        Page<TestUser> p = db.query("select u from TestUser u where u.status = :s order by u.age",
                        TestUser.class)
                .param("s", "ACTIVE")
                .page("select count(u) from TestUser u where u.status = :s",
                        PageRequest.of(0, 2));

        assertEquals(2, p.numberOfElements());
        assertEquals(3, p.totalElements());
    }

    /**
     * The page query needs {@code :status} and {@code :minAge}; the count query only
     * needs {@code :status}. This is a perfectly reasonable pair of statements, but the
     * executor blindly binds every entry of the map to both queries.
     */
    @Test
    void page_countQueryWithFewerParameters_shouldStillWork() {
        Page<TestUser> p = db.query(
                        "select u from TestUser u where u.status = :status order by abs(u.age - :pivot)",
                        TestUser.class)
                .param("status", "ACTIVE")
                .param("pivot", 22)
                .page("select count(u) from TestUser u where u.status = :status",
                        PageRequest.of(0, 2));

        assertEquals(2, p.numberOfElements());
        assertEquals(3, p.totalElements(), "Alice, Bob and Eve are ACTIVE");
    }

    /**
     * Opposite direction: the count query needs a parameter the page query does not.
     * This is a genuinely ambiguous request, so the only requirement is that it fails
     * loudly instead of producing a wrong total.
     */
    @Test
    void page_countQueryWithExtraParameters_failsLoudly() {
        assertThrows(RuntimeException.class,
                () -> db.query("select u from TestUser u order by u.age", TestUser.class)
                        .page("select count(u) from TestUser u where u.status = :status",
                                PageRequest.of(0, 2)));
    }

    @Test
    void list_strayParameterIsRejected() {
        // A stray parameter is a user error for a single query and must stay an error.
        assertThrows(RuntimeException.class,
                () -> db.query("select u from TestUser u", TestUser.class)
                        .param("unused", 1)
                        .list());
    }

    @Test
    void nullParameterValue_isBoundFollowingSqlSemantics() {
        db.save(new TestUser("NoStatus", 99, null));
        List<TestUser> users = db.query(
                        "select u from TestUser u where u.status = :s", TestUser.class)
                .param("s", null)
                .list();
        // `status = null` matches nothing in SQL, but binding must not throw.
        assertTrue(users.isEmpty());
    }

    @Test
    void duplicateParamName_lastOneWins() {
        List<TestUser> users = db.query(
                        "select u from TestUser u where u.name = :n", TestUser.class)
                .param("n", "Alice")
                .param("n", "Bob")
                .list();
        assertEquals(1, users.size());
        assertEquals("Bob", users.get(0).getName());
    }
}
