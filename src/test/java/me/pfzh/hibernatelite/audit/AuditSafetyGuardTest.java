package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariants for the "safety guard" feature.
 *
 * <p>Source being analysed: {@code QueryExecutor.delete()} / {@code QueryExecutor.update()}</p>
 *
 * <pre>
 * if (conditions.isEmpty() && (specs == null || specs.isEmpty())) {
 *     throw new HibernateLiteException("refusing to delete all rows ...");
 * }
 * ...
 * Predicate where = combine(cb, root, conditions, specs, meta);
 * if (where != null) { cd.where(where); }      // &lt;-- silently becomes DELETE FROM t
 * </pre>
 *
 * <p>The guard only checks that the <i>lists</i> are non-empty. It never checks that
 * the combined predicate is non-null, and {@code QuerySpec} is explicitly allowed to
 * return {@code null} ("means no additional restriction"). Therefore a query that
 * carries a spec which contributes nothing produces an unrestricted DELETE/UPDATE.</p>
 */
class AuditSafetyGuardTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-guard");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25),
                new TestUser("Bob", 17),
                new TestUser("Charlie", 30),
                new TestUser("Dave", 15),
                new TestUser("Eve", 22)));
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- delete ----------

    @Test
    void delete_withSpecReturningNull_mustNotWipeTheTable() {
        assertThrows(RuntimeException.class,
                () -> db.query(TestUser.class).where((cb, root) -> null).delete(),
                "a spec returning null contributes no restriction, so the guard must still refuse");

        assertEquals(5, db.query(TestUser.class).count(), "the whole table was deleted");
    }

    @Test
    void delete_withConditionButNullSpec_mustNotWipeIgnoringCondition() {
        // There is a real DSL condition here, so the guard passes - but the query
        // builder is fine with it; this one is expected to delete only matching rows.
        int deleted = db.query(TestUser.class)
                .lt(TestUser::getAge, 18)
                .where((cb, root) -> null)
                .delete();
        assertEquals(2, deleted);
        assertEquals(3, db.query(TestUser.class).count());
    }

    @Test
    void delete_withEmptyInList_mustNotWipeTheTable() {
        // An empty IN list is rejected inside QueryBuilder, before executeUpdate.
        assertThrows(RuntimeException.class,
                () -> db.query(TestUser.class).in(TestUser::getName, List.of()).delete());
        assertEquals(5, db.query(TestUser.class).count());
    }

    // ---------- update ----------

    @Test
    void update_withSpecReturningNull_mustNotUpdateTheWholeTable() {
        assertThrows(RuntimeException.class,
                () -> db.query(TestUser.class)
                        .where((cb, root) -> null)
                        .update(TestUser::getStatus, "PWNED"),
                "a spec returning null contributes no restriction, so the guard must still refuse");

        assertEquals(0, db.query(TestUser.class).eq(TestUser::getStatus, "PWNED").count(),
                "the whole table was updated");
    }

    @Test
    void update_withConditionAndNullSpec_onlyTouchesMatchingRows() {
        int updated = db.query(TestUser.class)
                .lt(TestUser::getAge, 18)
                .where((cb, root) -> null)
                .update(TestUser::getStatus, "MINOR");
        assertEquals(2, updated);
        assertEquals(2, db.query(TestUser.class).eq(TestUser::getStatus, "MINOR").count());
        assertEquals(3, db.query(TestUser.class).isNull(TestUser::getStatus).count());
    }

    // ---------- always-true spec ----------

    @Test
    void delete_withAlwaysTrueSpec_isExplicitAndAllowed() {
        // cb.conjunction() is an intentional "match everything"; the caller asked for it.
        int deleted = db.query(TestUser.class).where((cb, root) -> cb.conjunction()).delete();
        assertEquals(5, deleted);
    }

    // ---------- spec that throws ----------

    @Test
    void delete_withThrowingSpec_doesNotDeleteAnything() {
        assertThrows(IllegalStateException.class,
                () -> db.query(TestUser.class)
                        .where((cb, root) -> { throw new IllegalStateException("boom"); })
                        .delete());
        assertEquals(5, db.query(TestUser.class).count());
    }
}
