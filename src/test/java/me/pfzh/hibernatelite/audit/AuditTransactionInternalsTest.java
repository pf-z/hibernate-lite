package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.internal.SessionContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariants of the transaction engine.
 *
 * <p>Source being analysed: {@code internal/SessionContext.java}</p>
 *
 * <pre>
 * public static void commit() {
 *     h.depth--;
 *     if (h.depth &gt; 0) return;
 *     if (h.tx == null) { closeIfOwner(h); return; }
 *     if (h.tx.getStatus() == MARKED_ROLLBACK) { ... throw ... }
 *     try {
 *         if (h.tx.isActive()) { h.tx.commit(); }   // &lt;-- no else branch
 *     } catch (RuntimeException e) { ... }
 *     finally { h.tx = null; closeIfOwner(h); }
 * }
 * </pre>
 *
 * <p><b>Invariant violated:</b> a transaction that is no longer active is treated as
 * "committed successfully". The caller gets a normal return value while the data was
 * discarded. This is reachable through the documented escape hatch
 * ({@code SessionContext.current(sf)}, {@code unwrap(SessionFactory.class)}) and also
 * when the database/Hibernate has already rolled the transaction back (timeout, deadlock,
 * connection reset).</p>
 */
class AuditTransactionInternalsTest {

    private DataStore db;
    private HikariDataSource ds;
    private SessionFactory sf;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-txint");
        db = AuditSupport.newDataStore(ds, TestUser.class, NotNullEntity.class);
        sf = db.unwrap(SessionFactory.class);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    private long rawCount(Class<?> type) {
        try (var s = sf.openSession()) {
            return s.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class)
                    .getSingleResult();
        }
    }

    // ---------- commit on a non-active transaction ----------

    @Test
    void externalRollback_thenOuterCommit_mustNotReportSuccess() {
        // The escape hatch is publicly documented, so this sequence is reachable.
        assertThrows(RuntimeException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("Alice"));
                    SessionContext.current(sf).getTransaction().rollback();
                    return null;
                }),
                "commit() silently returned success although the transaction had been rolled back");

        assertEquals(0, rawCount(TestUser.class), "the insert must not survive");
    }

    @Test
    void externalRollback_leavesTheThreadUsable() {
        try {
            db.transaction(() -> {
                db.save(new TestUser("Alice"));
                SessionContext.current(sf).getTransaction().rollback();
                return null;
            });
        } catch (RuntimeException expected) {
            // any loud failure is acceptable; silence is not (see the test above)
        }
        assertFalse(SessionContext.inTransaction());
        db.save(new TestUser("after"));
        assertEquals(1, rawCount(TestUser.class));
    }

    @Test
    void externalCommit_thenOuterCommit_mustBeReportedLoudly() {
        // Committing the transaction behind the framework's back and then letting the
        // framework "commit" again must not be reported as a clean success.
        assertThrows(RuntimeException.class, () -> db.transaction(() -> {
            db.save(new TestUser("Alice"));
            SessionContext.current(sf).getTransaction().commit();
            return null;
        }));
        // The manual commit itself did persist the row; what matters is that the caller
        // learns the outer commit did not happen.
        assertEquals(1, rawCount(TestUser.class));
        assertFalse(SessionContext.inTransaction());
    }

    // ---------- commit failure ----------

    @Test
    void commitFailure_isReportedAndContextIsCleaned() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("ok"));
                    db.save(new NotNullEntity(null));   // NOT NULL violation at flush
                    return null;
                }));
        assertTrue(ex.getMessage().toLowerCase().contains("commit")
                        || ex.getCause() != null,
                "unexpected message: " + ex.getMessage());

        assertFalse(SessionContext.inTransaction(), "context leaked after a commit failure");
        assertEquals(0, rawCount(TestUser.class));
        assertEquals(0, rawCount(NotNullEntity.class));

        // The thread must remain usable.
        db.save(new TestUser("after"));
        assertEquals(1, rawCount(TestUser.class));
    }

    // ---------- public SessionContext API misuse ----------

    @Test
    void commitWithoutTransaction_throws() {
        assertThrows(HibernateLiteException.class, () -> SessionContext.commit());
    }

    @Test
    void rollbackWithoutTransaction_isSilentNoOp() {
        assertDoesNotThrow(() -> SessionContext.rollback());
        assertFalse(SessionContext.inTransaction());
    }

    @Test
    void markRollbackOnlyWithoutTransaction_isSilentNoOp() {
        assertDoesNotThrow(() -> SessionContext.markRollbackOnly());
    }

    @Test
    void closeInsideTransaction_isIgnored() {
        db.transaction(() -> {
            SessionContext.close();                 // must not kill the active session
            assertTrue(SessionContext.inTransaction());
            db.save(new TestUser("still-works"));
            return null;
        });
        assertEquals(1, rawCount(TestUser.class));
    }

    @Test
    void closingTheCurrentSessionMidTransaction_breaksTheTransaction() {
        // Documents how fragile the hand-managed escape hatch is: the library does
        // not detect that its own Session was closed behind its back.
        assertThrows(RuntimeException.class, () ->
                db.transaction(() -> {
                    SessionContext.current(sf).close();
                    db.save(new TestUser("too-late"));
                    return null;
                }));
        assertFalse(SessionContext.inTransaction(), "context must not stay bound to a dead session");
    }

    // ---------- nested rollback-only, three levels ----------

    @Test
    void threeLevelNesting_noneOuterCatches_middleSwallows_outerMustRollback() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("L1"));
                    try {
                        db.transaction(() -> {
                            db.save(new TestUser("L2"));
                            try {
                                db.transaction(() -> {
                                    db.save(new TestUser("L3"));
                                    throw new IllegalStateException("deep failure");
                                });
                            } catch (IllegalStateException e) {
                                db.save(new TestUser("L2-after"));
                            }
                            return null;
                        });
                    } catch (RuntimeException e) {
                        db.save(new TestUser("L1-after"));
                    }
                    return null;
                }));

        assertTrue(ex.getMessage().contains("rollback-only"), ex.getMessage());
        assertEquals(0, rawCount(TestUser.class));
        assertFalse(SessionContext.inTransaction());
    }

    // ---------- after-commit hooks / state ----------

    @Test
    void successfulTransactionLeavesNoContext() {
        db.transaction(() -> db.save(new TestUser("x")));
        assertFalse(SessionContext.inTransaction());
        assertDoesNotThrow(() -> SessionContext.rollback());
    }

    @Test
    void failedTransactionLeavesNoContext() {
        assertThrows(RuntimeException.class, () -> db.transaction(() -> {
            throw new RuntimeException("x");
        }));
        assertFalse(SessionContext.inTransaction());
        assertDoesNotThrow(() -> SessionContext.rollback());
    }
}
