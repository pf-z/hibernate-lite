package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of concurrency claims and resource handling.
 *
 * <p>{@code README.md} claims the library is "thread-safe by design" and that
 * sessions are "cleaned up automatically". These tests try to falsify both.</p>
 */
class AuditConcurrencyTest {

    /**
     * Runs 300 sequential operations against a 2-connection pool.
     * If a Session (and therefore a JDBC connection) is leaked, the pool is
     * exhausted and a connection-timeout error is raised.
     */
    @Test
    void sequentialOperationsDoNotLeakConnections() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:audit-leak-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setMaximumPoolSize(2);
        ds.setConnectionTimeout(3000);

        DataStore db = AuditSupport.newDataStore(ds, TestUser.class);
        try {
            for (int i = 0; i < 300; i++) {
                db.save(new TestUser("u" + i, i));
                assertNotNull(db.find(TestUser.class, (long) (i + 1)));
            }
            assertEquals(300, db.query(TestUser.class).count());
        } finally {
            AuditSupport.quietClose(db, ds);
        }
    }

    /**
     * 8 threads x 25 inserts, each thread using its own Session.
     */
    @Test
    void concurrentInsertsAreAllPersisted() throws Exception {
        HikariDataSource ds = AuditSupport.newDataSource("audit-conc");
        DataStore db = AuditSupport.newDataStore(ds, TestUser.class);
        int threads = 8;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        for (int i = 0; i < perThread; i++) {
                            db.save(new TestUser("t" + tid + "-" + i, i));
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        throw new RuntimeException(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
            assertEquals(0, failures.get());
            assertEquals(threads * perThread, db.query(TestUser.class).count());
        } finally {
            pool.shutdownNow();
            AuditSupport.quietClose(db, ds);
        }
    }

    /**
     * Concurrent reads and writes must not corrupt the thread-local context.
     */
    @Test
    void concurrentReadsAndWrites() throws Exception {
        HikariDataSource ds = AuditSupport.newDataSource("audit-conc-rw");
        DataStore db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25),
                new TestUser("Bob", 30)));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 40; i++) {
                        if (tid % 2 == 0) {
                            db.save(new TestUser("w" + tid + "-" + i));
                        } else {
                            db.query(TestUser.class).count();
                            db.find(TestUser.class, 1L);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);

            assertEquals(4 * 40 + 2, db.query(TestUser.class).count());
        } finally {
            pool.shutdownNow();
            AuditSupport.quietClose(db, ds);
        }
    }

    /**
     * Concurrent nested transactions on separate threads.
     */
    @Test
    void concurrentNestedTransactions() throws Exception {
        HikariDataSource ds = AuditSupport.newDataSource("audit-conc-nested");
        DataStore db = AuditSupport.newDataStore(ds, TestUser.class);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < 6; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 10; i++) {
                        final int idx = i;
                        db.transaction(() -> {
                            db.save(new TestUser("o" + tid + "-" + idx));
                            db.transaction(() -> {
                                db.save(new TestUser("i" + tid + "-" + idx));
                                return null;
                            });
                            return null;
                        });
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
            assertEquals(120, db.query(TestUser.class).count());
        } finally {
            pool.shutdownNow();
            AuditSupport.quietClose(db, ds);
        }
    }

    /**
     * A failing transaction on a worker thread must not poison the pooled thread
     * for later work.
     */
    @Test
    void failingTransactionsOnPooledThreadsDoNotPoisonThem() throws Exception {
        HikariDataSource ds = AuditSupport.newDataSource("audit-conc-fail");
        DataStore db = AuditSupport.newDataStore(ds, TestUser.class);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 20; i++) {
                final int n = i;
                pool.submit(() -> {
                    try {
                        db.transaction(() -> {
                            db.save(new TestUser("fail-" + n));
                            throw new IllegalStateException("boom");
                        });
                    } catch (IllegalStateException expected) {
                        // ignore
                    }
                }).get(30, TimeUnit.SECONDS);
            }
            // Same single thread reused for all 20 failures.
            assertEquals(0, db.query(TestUser.class).count());
            db.save(new TestUser("after"));
            assertEquals(1, db.query(TestUser.class).count());
        } finally {
            pool.shutdownNow();
            AuditSupport.quietClose(db, ds);
        }
    }
}
