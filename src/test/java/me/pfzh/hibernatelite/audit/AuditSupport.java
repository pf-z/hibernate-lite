package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.internal.SessionContext;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared helpers for the independent audit test suite.
 *
 * <p>These tests were written from scratch by the reviewer, using only the
 * public API and the promises made in {@code README.md} / Javadoc as the
 * expected behaviour.</p>
 *
 * <p><b>Why {@link #resetThreadContext()} is needed:</b> {@code SessionContext}
 * is a static {@link ThreadLocal}, so a test that intentionally leaves a
 * transaction open (to prove a defect) would otherwise poison every later test
 * running on the same Surefire thread.</p>
 */
final class AuditSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private AuditSupport() {}

    static void resetThreadContext() {
        try {
            SessionContext.rollback();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    static HikariDataSource newDataSource(String tag) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + tag + "-" + System.nanoTime() + "-"
                + SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setMaximumPoolSize(16);
        return ds;
    }

    static DataStore newDataStore(HikariDataSource ds, Class<?>... entities) {
        resetThreadContext();
        return HibernateLite.builder()
                .dataSource(ds)
                .entities(entities)
                .ddlAuto("create-drop")
                .build();
    }

    static void quietClose(DataStore db, HikariDataSource ds) {
        resetThreadContext();
        try {
            if (db != null) db.close();
        } catch (RuntimeException ignored) {
            // best effort in teardown
        }
        try {
            if (ds != null) ds.close();
        } catch (RuntimeException ignored) {
            // best effort in teardown
        }
    }
}
