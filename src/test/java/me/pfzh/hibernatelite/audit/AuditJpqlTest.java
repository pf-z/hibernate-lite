package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of the JPQL module.
 *
 * <p>Expectations are taken from {@code README.md} section "4. JPQL".</p>
 */
class AuditJpqlTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-jpql");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25, "ACTIVE"),
                new TestUser("Bob", 17, "ACTIVE"),
                new TestUser("Charlie", 30, "INACTIVE"),
                new TestUser("Dave", 15, "INACTIVE"),
                new TestUser("Eve", 22, "ACTIVE")
        ));
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    @Test
    void listWithNamedParameters() {
        List<TestUser> users = db.query(
                        "select u from TestUser u where u.status = :status and u.age > :minAge",
                        TestUser.class)
                .param("status", "ACTIVE")
                .param("minAge", 18)
                .list();

        assertEquals(2, users.size());   // Alice 25, Eve 22
    }

    @Test
    void listWithLimit() {
        assertEquals(2, db.query("select u from TestUser u order by u.age", TestUser.class)
                .list(2).size());
    }

    @Test
    void oneReturnsEntity() {
        TestUser u = db.query("select u from TestUser u where u.name = :n", TestUser.class)
                .param("n", "Alice")
                .one();
        assertNotNull(u);
        assertEquals(25, u.getAge());
    }

    @Test
    void oneNoMatchReturnsNull() {
        assertNull(db.query("select u from TestUser u where u.name = :n", TestUser.class)
                .param("n", "Nobody")
                .one());
    }

    @Test
    void oneMultipleMatchesThrows() {
        HibernateLiteException e = assertThrows(HibernateLiteException.class,
                () -> db.query("select u from TestUser u where u.status = :s", TestUser.class)
                        .param("s", "ACTIVE")
                        .one());
        assertTrue(e.getMessage().contains("more than one"));
    }

    @Test
    void countReturnsScalar() {
        assertEquals(3, db.query(
                        "select count(u) from TestUser u where u.status = :s", Long.class)
                .param("s", "ACTIVE")
                .count());
    }

    @Test
    void countOnEmptyResultReturnsZero() {
        assertEquals(0, db.query(
                        "select count(u) from TestUser u where u.name = :n", Long.class)
                .param("n", "Nobody")
                .count());
    }

    @Test
    void existsTrueFalse() {
        assertTrue(db.query("select u from TestUser u where u.name = :n", TestUser.class)
                .param("n", "Alice").exists());
        assertFalse(db.query("select u from TestUser u where u.name = :n", TestUser.class)
                .param("n", "Nobody").exists());
    }

    @Test
    void pageWithCountJpql() {
        Page<TestUser> p = db.query(
                        "select u from TestUser u order by u.age",
                        TestUser.class)
                .page("select count(u) from TestUser u", PageRequest.of(0, 2));

        assertEquals(2, p.numberOfElements());
        assertEquals(5, p.totalElements());
        assertEquals(3, p.totalPages());
        assertTrue(p.hasNext());
    }

    @Test
    void pageWithoutCount_requiresNullCountJpql() {
        Page<TestUser> p = db.query("select u from TestUser u order by u.age", TestUser.class)
                .page(null, PageRequest.of(0, 2).withoutCount());
        assertEquals(2, p.numberOfElements());
        assertFalse(p.hasTotal());
        assertTrue(p.hasNext());
    }

    /**
     * README documents that {@code page(PageRequest)} throws
     * {@code UnsupportedOperationException} because a count JPQL is required.
     * In slice mode no count query is needed at all, so the single-argument
     * overload is unusable even though it could work.
     */
    @Test
    void pageSingleArg_sliceModeIsNotSupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> db.query("select u from TestUser u", TestUser.class)
                        .page(PageRequest.of(0, 2).withoutCount()));
    }

    @Test
    void pageCountTotalWithoutCountJpqlFails() {
        assertThrows(HibernateLiteException.class,
                () -> db.query("select u from TestUser u", TestUser.class)
                        .page("", PageRequest.of(0, 2)));
    }

    @Test
    void nullOrBlankJpqlRejected() {
        assertThrows(IllegalArgumentException.class, () -> db.query(null, TestUser.class));
        assertThrows(IllegalArgumentException.class, () -> db.query("   ", TestUser.class));
    }

    @Test
    void nullResultTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("select u from TestUser u", null));
    }

    @Test
    void syntaxErrorIsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("selec u frm TestUser u", TestUser.class).list());
    }

    @Test
    void unknownNamedParameterFails() {
        assertThrows(RuntimeException.class,
                () -> db.query("select u from TestUser u where u.name = :n", TestUser.class)
                        .param("wrongName", "Alice")
                        .list());
    }

    @Test
    void countOnNonCountQueryFails() {
        assertThrows(RuntimeException.class,
                () -> db.query("select u from TestUser u", TestUser.class).count());
    }

    @Test
    void nullParamNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("select u from TestUser u", TestUser.class).param(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> db.query("select u from TestUser u", TestUser.class).param("  ", 1));
    }

    @Test
    void listZeroLimitRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("select u from TestUser u", TestUser.class).list(0));
    }

    @Test
    void updateAndDeleteThroughJpqlAreNotOffered_butEscapeHatchWorks() {
        // Documented: native SQL / DML must go through unwrap(SessionFactory.class).
        var sf = db.unwrap(org.hibernate.SessionFactory.class);
        try (var s = sf.openSession()) {
            s.beginTransaction();
            int n = s.createMutationQuery("update TestUser u set u.status = 'X' where u.age < 18")
                    .executeUpdate();
            s.getTransaction().commit();
            assertEquals(2, n);
        }
        assertEquals(2, db.query(TestUser.class).eq(TestUser::getStatus, "X").count());
    }

    @Test
    void groupByAndAggregate() {
        List<Object[]> rows = queryRaw("select u.status, count(u) from TestUser u group by u.status");
        assertEquals(2, rows.size());
    }

    // Helper: JpqlQuery is typed, so use Object[] as result type.
    private List<Object[]> queryRaw(String jpql) {
        return db.query(jpql, Object[].class).list();
    }
}
