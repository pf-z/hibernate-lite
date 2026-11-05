package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.internal.SessionContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of transaction semantics.
 *
 * <p>Expectations are taken from {@code README.md} sections
 * "6. Transactions" and "Design Principles".</p>
 */
class AuditTransactionTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-tx");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        // Make sure a previous failing test cannot poison this thread.
        SessionContext.rollback();
    }

    @AfterEach
    void tearDown() {
        SessionContext.rollback();
        AuditSupport.quietClose(db, ds);
    }

    private long rawCount() {
        try (var s = db.unwrap(SessionFactory.class).openSession()) {
            return s.createQuery("select count(u) from TestUser u", Long.class).getSingleResult();
        }
    }

    // ---------- basic semantics ----------

    @Test
    void commitOnSuccess() {
        Long id = db.transaction(() -> db.save(new TestUser("Alice")).getId());
        assertNotNull(id);
        assertEquals(1, rawCount());
    }

    @Test
    void rollbackOnRuntimeException() {
        assertThrows(IllegalStateException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("Alice"));
                    throw new IllegalStateException("boom");
                }));
        assertEquals(0, rawCount(), "runtime exception must roll back");
        assertFalse(SessionContext.inTransaction(), "transaction context must be cleaned up");
    }

    @Test
    void rollbackOnError_mustAlsoCleanUp() {
        // Expected: any Throwable aborts the transaction and releases the
        // session/ThreadLocal. Otherwise the calling thread is permanently
        // stuck "inside" a transaction and its JDBC connection is leaked.
        assertThrows(AssertionError.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("Alice"));
                    throw new AssertionError("simulated fatal error");
                }));

        // Measured on a separate connection, so the value reflects what was
        // really committed.
        try (var s = db.unwrap(SessionFactory.class).openSession()) {
            assertEquals(0L, s.createQuery("select count(u) from TestUser u", Long.class)
                    .getSingleResult(), "Error thrown inside transaction must roll back");
        }

        Integer active = ds.getHikariPoolMXBean().getActiveConnections();
        assertAll(
                () -> assertFalse(SessionContext.inTransaction(),
                        "Error thrown inside transaction left the thread stuck in a transaction"),
                () -> assertEquals(0, active,
                        "Error thrown inside transaction leaked " + active
                                + " JDBC connection(s): the session was never closed")
        );
    }

    @Test
    void transactionReturnsValue() {
        assertEquals("result", db.transaction(() -> "result"));
    }

    @Test
    void transactionMayReturnNull() {
        assertNull(db.transaction(() -> null));
    }

    @Test
    void runnableOverload() {
        db.transaction(() -> db.save(new TestUser("Bob")));
        assertEquals(1, rawCount());
    }

    @Test
    void nullCallbackRejected() {
        // NOTE: the library is inconsistent here - CrudExecutor throws
        // IllegalArgumentException for null arguments, while DataStoreImpl /
        // TransactionManager use Objects.requireNonNull (NullPointerException).
        assertThrows(RuntimeException.class,
                () -> db.transaction(
                        (me.pfzh.hibernatelite.transaction.TransactionCallback<Object>) null));
    }

    @Test
    void contextIsCleanedAfterSuccess() {
        db.transaction(() -> {
            assertTrue(SessionContext.inTransaction());
            return null;
        });
        assertFalse(SessionContext.inTransaction());
    }

    @Test
    void contextIsCleanedAfterFailure() {
        assertThrows(RuntimeException.class, () -> db.transaction(() -> {
            throw new RuntimeException("x");
        }));
        assertFalse(SessionContext.inTransaction());
    }

    // ---------- nesting ----------

    @Test
    void nestedTransactionReusesOuter() {
        db.transaction(() -> {
            db.save(new TestUser("outer-1"));
            db.transaction(() -> {
                db.save(new TestUser("inner-1"));
                db.transaction(() -> db.save(new TestUser("inner-2")));
                return null;
            });
            db.save(new TestUser("outer-2"));
            return null;
        });
        assertEquals(4, rawCount());
    }

    @Test
    void nestedFailurePropagatesAndRollsBackAll() {
        assertThrows(IllegalStateException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("outer-1"));
                    db.transaction(() -> {
                        db.save(new TestUser("inner-1"));
                        throw new IllegalStateException("inner boom");
                    });
                    return null;
                }));
        assertEquals(0, rawCount());
    }

    @Test
    void nestedFailureSwallowed_outerRollsBack() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class, () ->
                db.transaction(() -> {
                    db.save(new TestUser("outer-1"));
                    try {
                        db.transaction(() -> {
                            db.save(new TestUser("inner-1"));
                            throw new IllegalStateException("inner boom");
                        });
                    } catch (IllegalStateException swallowed) {
                        // user swallows
                    }
                    db.save(new TestUser("outer-2"));
                    return null;
                }));

        assertTrue(ex.getMessage().contains("rollback-only"), ex.getMessage());
        assertEquals(0, rawCount(), "swallowed inner failure must still roll back everything");
    }

    @Test
    void nestedFailureSwallowedThenUserFindsFreshTransaction_mustWork() {
        assertThrows(HibernateLiteException.class, () -> db.transaction(() -> {
            try {
                db.transaction(() -> {
                    throw new IllegalStateException("inner");
                });
            } catch (IllegalStateException ignored) {
            }
            return null;
        }));

        // The thread must be clean afterwards.
        assertFalse(SessionContext.inTransaction());
        db.save(new TestUser("aftermath"));
        assertEquals(1, rawCount(), "library must recover after a rollback-only transaction");
    }

    @Test
    void deeplyNested10Levels() {
        db.transaction(() -> {
            nest(10);
            return null;
        });
        assertEquals(10, rawCount());
    }

    private void nest(int depth) {
        db.save(new TestUser("level-" + depth));
        if (depth > 1) {
            db.transaction(() -> {
                nest(depth - 1);
                return null;
            });
        }
    }

    // ---------- isolation between DataStore instances ----------

    @Test
    void twoDataStores_sequentialUse_isIsolated() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-tx-2");
        DataStore db2 = AuditSupport.newDataStore(ds2, TestUser.class);
        try {
            db.save(new TestUser("in-db1"));
            db2.save(new TestUser("in-db2"));

            assertEquals(1, db.query(TestUser.class).count());
            assertEquals(1, db2.query(TestUser.class).count());
            assertEquals("in-db1", db.query(TestUser.class).list().get(0).getName());
            assertEquals("in-db2", db2.query(TestUser.class).list().get(0).getName());
        } finally {
            AuditSupport.quietClose(db2, ds2);
        }
    }

    @Test
    void twoDataStores_nestedInTransaction_mustStayIsolated() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-tx-3");
        DataStore db2 = AuditSupport.newDataStore(ds2, TestUser.class);
        try {
            // db2 is used *while db1 owns the thread-local transaction context*.
            // Each DataStore must operate on its own Session/SessionFactory.
            db.transaction(() -> {
                db2.save(new TestUser("should-land-in-db2"));
                return null;
            });

            long inDb1 = db.query(TestUser.class).count();
            long inDb2 = db2.query(TestUser.class).count();

            assertAll(
                    () -> assertEquals(1, inDb2,
                            "db2.save() ran inside db1.transaction() and never reached db2 "
                                    + "(rows actually written to db1: " + inDb1 + ")"),
                    () -> assertEquals(0, inDb1,
                            "db2.save() was written into db1's database instead of db2's")
            );
        } finally {
            AuditSupport.quietClose(db2, ds2);
        }
    }

    // ---------- thread isolation ----------

    @Test
    void threadLocalIsolation() throws Exception {
        CountDownLatch outerStarted = new CountDownLatch(1);
        CountDownLatch workerDone = new CountDownLatch(1);
        AtomicReference<Boolean> workerSawTransaction = new AtomicReference<>();
        AtomicReference<Throwable> workerError = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                outerStarted.await(5, TimeUnit.SECONDS);
                workerSawTransaction.set(SessionContext.inTransaction());
                db.save(new TestUser("from-worker"));
            } catch (Throwable t) {
                workerError.set(t);
            } finally {
                workerDone.countDown();
            }
        }, "audit-worker");
        worker.start();

        db.transaction(() -> {
            db.save(new TestUser("from-main"));
            outerStarted.countDown();
            try {
                assertTrue(workerDone.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        worker.join(5000);

        assertNull(workerError.get(), String.valueOf(workerError.get()));
        assertEquals(Boolean.FALSE, workerSawTransaction.get(),
                "worker thread must not inherit the caller's transaction");
        assertEquals(2, rawCount(), "both the outer and the worker insert must be committed");
    }
}
