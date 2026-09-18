package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;

/**
 * Thread-local Hibernate session and transaction context.
 *
 * <p>This class manages the lifecycle of the Session and Transaction
 * associated with the current thread.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Bind Session to the current thread through ThreadLocal.</li>
 *     <li>Manage transaction lifecycle.</li>
 *     <li>Support nested transactions through depth counting.</li>
 *     <li>Release only resources created by this library.</li>
 * </ul>
 *
 * <p>Hibernate does not support true nested transactions.
 * Nested transactions are simulated by delaying commit until the outermost
 * transaction completes.</p>
 *
 * <p>This is an internal component and should not be used directly by users.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class SessionContext {

    /**
     * Stores transaction context independently for each thread.
     *
     * <p>ThreadLocal provides thread isolation, so no additional
     * synchronization is required.</p>
     */
    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    /**
     * Utility class.
     */
    private SessionContext() {
    }

    /**
     * Holds the session and transaction state of the current thread.
     */
    private static final class Holder {

        /**
         * Hibernate session bound to current thread.
         */
        Session session;

        /**
         * Current database transaction.
         */
        Transaction tx;

        /**
         * Transaction nesting depth.
         *
         * <p>depth == 0: no active transaction.</p>
         * <p>depth == 1: outermost transaction.</p>
         * <p>depth > 1: nested transaction.</p>
         *
         * <p>This counter does not create real nested database transactions.
         * It only controls when the actual commit happens.</p>
         */
        int depth;

        /**
         * Whether this Session was created by HibernateLite.
         *
         * <p>Only sessions owned by this library should be closed here.
         * External sessions must not be closed by this class.</p>
         */
        boolean owner;      // true = 本库打开的，负责关闭
    }

    /**
     * Gets the current thread's Session.
     *
     * <p>If no Session exists, a new one is created and bound to the thread.</p>
     */
    public static Session current(SessionFactory factory) {
        Holder h = CURRENT.get();
        if (h == null) {
            h = new Holder();
            h.session = factory.openSession();
            h.owner = true;
            CURRENT.set(h);
        }
        return h.session;
    }

    /**
     * Starts a transaction.
     *
     * <p>If a transaction already exists, only the nesting depth is increased.
     * The actual Hibernate transaction is created only at the outermost level.</p>
     */
    public static void begin(SessionFactory factory) {
        Holder h = CURRENT.get();
        if (h == null) {
            h = new Holder();
            h.session = factory.openSession();
            h.owner = true;
            CURRENT.set(h);
        }
        if (h.depth == 0) {
            try {
                h.tx = h.session.beginTransaction();
            } catch (RuntimeException e) {
                closeIfOwner(h);
                throw new HibernateLiteException("Failed to begin transaction", e);
            }
        }
        h.depth++;
    }


    /**
     * Commits the current transaction.
     *
     * <p>Only the outermost transaction performs the real commit.
     * Inner transactions only decrease the nesting depth.</p>
     *
     * <p>If an inner operation marked the transaction as rollback-only,
     * the outermost commit will rollback instead.</p>
     */
    public static void commit() {
        Holder h = CURRENT.get();
        if (h == null) {
            throw new HibernateLiteException("No transaction context");
        }
        if (h.depth <= 0) {
            throw new HibernateLiteException("Invalid transaction depth");
        }

        h.depth--;

        // Nested transaction: wait for outer transaction.
        if (h.depth > 0) {
            return;
        }

        if (h.tx == null) {
            closeIfOwner(h);
            return;
        }

        /*
         * An inner operation may have marked this transaction as rollback-only.
         *
         * Even if the original exception was handled,
         * the transaction must not be committed.
         */
        if (h.tx.getStatus() == TransactionStatus.MARKED_ROLLBACK) {
            try {
                h.tx.rollback();
            } catch (RuntimeException ignored) {

                // Rollback failure cannot be recovered.
            } finally {
                h.tx = null;
                closeIfOwner(h);
            }
            throw new HibernateLiteException("\"Transaction marked rollback-only");
        }

        // Normal transaction commit.
        try {
            if (h.tx.isActive()) {
                h.tx.commit();
            }
        } catch (RuntimeException e) {
            // Commit failure: attempt rollback to restore consistency.
            tryRollback(h);
            closeIfOwner(h);
            throw new HibernateLiteException("Failed to commit transaction", e);
        } finally {
            // Always release transaction and session resources.
            h.tx = null;
            closeIfOwner(h);
        }
    }

    /**
     * Rolls back the current transaction.
     *
     * <p>Nested transaction behavior:</p>
     * <ul>
     *     <li>Outermost transaction: rollback immediately.</li>
     *     <li>Nested transaction: mark rollback-only and let the outermost
     *     transaction perform the rollback.</li>
     * </ul>
     */
    public static void rollback() {
        Holder h = CURRENT.get();
        if (h == null) return;

        if (h.depth <= 1) {
            // Outermost transaction: rollback and release resources.
            tryRollback(h);
            h.depth = 0;
            closeIfOwner(h);
        } else {
            // Nested transaction: defer rollback to outer transaction.
            h.depth--;
            markRollbackOnlyInternal(h);
        }
    }

    /**
     * Marks the current transaction as rollback-only.
     *
     * <p>This is used when an inner operation fails but the exception
     * is handled by user code. The outer transaction must still rollback.</p>
     */
    public static void markRollbackOnly() {
        Holder h = CURRENT.get();
        markRollbackOnlyInternal(h);
    }

    /**
     * Checks whether the current thread is inside a transaction.
     */
    public static boolean inTransaction() {
        Holder h = CURRENT.get();
        return h != null && h.depth > 0;
    }

    /**
     * Closes the current Session when no transaction is active.
     *
     * <p>Used by operations that do not require explicit transactions.</p>
     */
    public static void close() {
        Holder h = CURRENT.get();
        if (h == null) return;
        // Active transactions must keep the Session alive.
        if (h.depth > 0) return;
        closeIfOwner(h);
    }

    /**
     * Test-only cleanup method.
     *
     * <p>Clears ThreadLocal state and releases resources after tests.</p>
     */
    static void reset() {
        Holder h = CURRENT.get();
        if (h == null) return;
        try {
            if (h.tx != null && h.tx.isActive()) {
                h.tx.rollback();
            }
        } catch (RuntimeException ignored) {
            // Ignore cleanup failures during tests.
        }
        closeIfOwner(h);
    }

    /**
     * Marks transaction as rollback-only internally.
     */
    private static void markRollbackOnlyInternal(Holder h) {
        if (h == null || h.tx == null) return;
        try {
            h.tx.markRollbackOnly();
        } catch (RuntimeException ignored) {
            // Do not hide the original exception.
        }
    }

    /**
     * Attempts to rollback the transaction.
     */
    private static void tryRollback(Holder h) {
        if (h.tx != null && h.tx.isActive()) {
            try {
                h.tx.rollback();
            } catch (RuntimeException ignored) {
                // Rollback failure cannot be recovered.
            }
        }
    }

    /**
     * Closes the Session if it is owned by HibernateLite.
     *
     * <p>Always removes ThreadLocal reference to prevent leaks
     * when threads are reused by thread pools.</p>
     */
    private static void closeIfOwner(Holder h) {
        if (h.owner && h.session != null) {
            try {
                if (h.session.isOpen()) {
                    h.session.close();
                }
            } catch (RuntimeException ignored) {
                // Closing failure cannot be recovered.
            }
        }
        // Prevent stale context from remaining in reused threads.
        CURRENT.remove();
    }

}