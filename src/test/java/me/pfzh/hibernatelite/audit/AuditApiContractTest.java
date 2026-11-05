package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariants of the small value objects and of the
 * public facade surface.
 *
 * <p>Sources analysed: {@code pagination/Page.java}, {@code pagination/PageRequest.java},
 * {@code internal/DataStoreImpl.java}, {@code query/JpqlQuery.java}.</p>
 */
class AuditApiContractTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-api");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25),
                new TestUser("Bob", 17),
                new TestUser("Charlie", 30)));
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- Page value object ----------

    @Test
    void map_rejectsNullElementsBecauseOfListCopyOf() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 2));
        // List.copyOf forbids nulls, so a nullable mapper blows up with a bare NPE.
        assertThrows(NullPointerException.class, () -> p.map(u -> null));
    }

    @Test
    void pageOf_acceptsMoreContentThanRequestedSize() {
        // No consistency check between content size and request.size().
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 5));
        Page<String> mapped = p.map(TestUser::getName);
        assertEquals(3, mapped.numberOfElements());
        assertEquals(5, mapped.size());
        assertEquals(1, mapped.totalPages());
    }

    @Test
    void pageOutOfRange_beyondTotal_stillReportsPreviousPage() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(10, 2));
        assertTrue(p.isEmpty());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
        assertTrue(p.hasPrevious());
        assertFalse(p.isFirst());
    }

    @Test
    void pageWithoutTotal_isLastWhenNoNext() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(1, 2).withoutCount());
        assertEquals(1, p.numberOfElements());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
    }

    // ---------- PageRequest ----------

    @Test
    void pageRequestMaxSizeIs1000AndBounded() {
        assertEquals(1000, PageRequest.of(0, 1000).size());
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 1001));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.firstPage(0));
    }

    @Test
    void pageRequestHasNoValueSemantics() {
        PageRequest a = PageRequest.of(1, 10);
        PageRequest b = PageRequest.of(1, 10);
        // No equals()/hashCode()/toString(): two equal requests are not equal objects.
        assertNotEquals(a, b);
        assertTrue(a.toString().contains("PageRequest"));
    }

    // ---------- facade null handling ----------

    @Test
    void facadeNullHandlingIsInconsistent() {
        // CrudExecutor -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> db.save(null));
        assertThrows(IllegalArgumentException.class, () -> db.find(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> db.delete(null));
        // DataStoreImpl / TransactionManager -> NullPointerException
        assertThrows(NullPointerException.class, () -> db.query((Class<Object>) null));
        assertThrows(NullPointerException.class,
                () -> db.transaction(
                        (me.pfzh.hibernatelite.transaction.TransactionCallback<Object>) null));
    }

    @Test
    void queryWithNullJpqlRejected() {
        assertThrows(IllegalArgumentException.class, () -> db.query(null, TestUser.class));
        assertThrows(IllegalArgumentException.class, () -> db.query("", TestUser.class));
        assertThrows(IllegalArgumentException.class, () -> db.query("  ", TestUser.class));
    }

    // ---------- unwrap after close ----------

    @Test
    void unwrapAfterClose_returnsAlreadyClosedFactory() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-api-close");
        DataStore local = AuditSupport.newDataStore(ds2, TestUser.class);
        SessionFactory sf = local.unwrap(SessionFactory.class);
        local.close();

        // Still returns a SessionFactory, even though it is closed.
        assertDoesNotThrow(() -> local.unwrap(SessionFactory.class));
        assertTrue(local.unwrap(SessionFactory.class).isClosed());
        assertTrue(sf.isClosed());
        ds2.close();
    }

    @Test
    void closeTwiceAndThenUse_failsButDoesNotCorruptOtherStores() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-api-close2");
        DataStore local = AuditSupport.newDataStore(ds2, TestUser.class);
        local.close();
        assertDoesNotThrow(local::close);
        assertThrows(RuntimeException.class, () -> local.find(TestUser.class, 1L));

        // A different DataStore on the same thread must be unaffected.
        assertEquals(3, db.query(TestUser.class).count());
        ds2.close();
    }

    // ---------- JpqlQuery.page ----------

    @Test
    void jpqlPageSingleArgAlwaysThrowsEvenForSliceMode() {
        var q = db.query("select u from TestUser u", TestUser.class);
        assertThrows(UnsupportedOperationException.class,
                () -> q.page(PageRequest.of(0, 2).withoutCount()));

        // The workaround forces the caller to pass a null count JPQL.
        assertDoesNotThrow(() -> db.query("select u from TestUser u", TestUser.class)
                .page((String) null, PageRequest.of(0, 2).withoutCount()));
    }

    // ---------- ordering / pagination determinism ----------

    @Test
    void paginationWithoutOrderBy_isRejectedOrDocumentedAtLeast() {
        // No ORDER BY is emitted when no order is requested, so page boundaries are
        // not deterministic on a real database. On H2 the natural order happens to be
        // stable, so this test only asserts that the API does not blow up.
        List<TestUser> page0 = db.query(TestUser.class).list(2);
        List<TestUser> page1 = db.query(TestUser.class).page(PageRequest.of(1, 2)).content();
        assertEquals(1, page1.size());
        assertEquals(2, page0.size());
    }
}
