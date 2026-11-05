package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p><b>Multiple SessionFactories:</b> state is kept per {@link SessionFactory},
 * not per thread only. Two {@code DataStore} instances used on the same thread
 * therefore get independent Sessions and independent transactions; a transaction
 * opened on one of them never captures operations issued through the other.</p>
 *
 * <p>Hibernate does not support true nested transactions. Nested transactions are
 * simulated by delaying commit until the outermost transaction completes.</p>
 *
 * <p>This is an internal component and should not be used directly by users.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class SessionContext {

    /**
     * Maps each SessionFactory to the state of the current thread for that factory.
     *
     * <p>Using a map (instead of a single holder) is what keeps two
     * {@code DataStore} instances from sharing one Session.</p>
     */
    private static final ThreadLocal<Map<SessionFactory, Holder>> CURRENT =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * Monotonic counter used to identify the most recently begun transaction,
     * which is what the factory-less convenience methods operate on.
     */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /**
     * Utility class.
     */
    private SessionContext() {
    }

    /**
     * Holds the session and transaction state of the current thread for one factory.
     */
    private static final class Holder {

        /** Factory owning {@link #session}. */
        SessionFactory factory;

        /** Hibernate session bound to current thread. */
        Session session;

        /** Current database transaction. */
        Transaction tx;

        /**
         * Transaction nesting depth.
         *
         * <p>depth == 0: no active transaction.</p>
         * <p>depth == 1: outermost transaction.</p>
         * <p>depth &gt; 1: nested transaction.</p>
         *
         * <p>This counter does not create real nested database transactions.
         * It only controls when the actual commit happens.</p>
         */
        int depth;

        /** Whether this Session was created by HibernateLite. */
        boolean owner;

        /** Order in which the current transaction started. */
        long begunAt;
    }

    private static Map<SessionFactory, Holder> holders() {
        return CURRENT.get();
    }

    private static Holder getOrCreateHolder(SessionFactory factory) {
        Map<SessionFactory, Holder> map = holders();
        Holder h = map.get(factory);
        if (h == null) {
            h = new Holder();
            h.factory = factory;
            h.session = factory.openSession();
            h.owner = true;
            map.put(factory, h);
        }
        return h;
    }

    /**
     * Gets the current thread's Session for the given factory.
     *
     * <p>If no Session exists for that factory, a new one is created and bound
     * to the thread.</p>
     *
     * @param factory the SessionFactory the session must belong to
     * @return the Session bound to the current thread for {@code factory}
     */
    public static Session current(SessionFactory factory) {
        return getOrCreateHolder(factory).session;
    }

    /**
     * Starts a transaction for the given factory.
     *
     * <p>If a transaction for that factory already exists, only the nesting depth
     * is increased. The actual Hibernate transaction is created only at the
     * outermost level.</p>
     *
     * @param factory the SessionFactory to start the transaction for
     */
    public static void begin(SessionFactory factory) {
        Holder h = getOrCreateHolder(factory);
        if (h.depth == 0) {
            try {
                h.tx = h.session.beginTransaction();
            } catch (RuntimeException e) {
                closeIfOwner(h);
                throw new HibernateLiteException("Failed to begin transaction", e);
            }
            h.begunAt = SEQUENCE.incrementAndGet();
        }
        h.depth++;
    }

    /**
     * Commits the transaction of the most recently begun factory.
     *
     * @see #commit(SessionFactory)
     */
    public static void commit() {
        commitHolder(requireHolder(primary()));
    }

    /**
     * Commits the transaction of the given factory.
     *
     * <p>Only the outermost transaction performs the real commit.
     * Inner transactions only decrease the nesting depth.</p>
     *
     * <p>If an inner operation marked the transaction as rollback-only,
     * the outermost commit will rollback instead.</p>
     *
     * @param factory the SessionFactory whose transaction should be committed
     */
    public static void commit(SessionFactory factory) {
        commitHolder(requireHolder(holders().get(factory)));
    }

    private static void commitHolder(Holder h) {
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
            throw new HibernateLiteException("Transaction marked rollback-only");
        }

        /*
         * A transaction that is no longer active was rolled back (or committed)
         * outside of this library. Reporting success here would silently discard
         * the caller's writes, so it must fail loudly instead.
         */
        if (!h.tx.isActive()) {
            tryRollback(h);
            h.tx = null;
            closeIfOwner(h);
            throw new HibernateLiteException(
                    "Transaction is no longer active; it was rolled back or committed outside "
                            + "of Hibernate-Lite, so nothing has been committed");
        }

        // Normal transaction commit.
        try {
            h.tx.commit();
        } catch (RuntimeException e) {
            // Commit failure: attempt rollback to restore consistency.
            tryRollback(h);
            throw new HibernateLiteException("Failed to commit transaction", e);
        } finally {
            // Always release transaction and session resources.
            h.tx = null;
            closeIfOwner(h);
        }
    }

    /**
     * Rolls back the transaction of the most recently begun factory.
     *
     * @see #rollback(SessionFactory)
     */
    public static void rollback() {
        rollbackHolder(primary());
    }

    /**
     * Rolls back the transaction of the given factory.
     *
     * <p>Nested transaction behavior:</p>
     * <ul>
     *     <li>Outermost transaction: rollback immediately.</li>
     *     <li>Nested transaction: mark rollback-only and let the outermost
     *     transaction perform the rollback.</li>
     * </ul>
     *
     * @param factory the SessionFactory whose transaction should be rolled back
     */
    public static void rollback(SessionFactory factory) {
        rollbackHolder(holders().get(factory));
    }

    private static void rollbackHolder(Holder h) {
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
     * Marks the transaction of the most recently begun factory as rollback-only.
     *
     * @see #markRollbackOnly(SessionFactory)
     */
    public static void markRollbackOnly() {
        markRollbackOnlyInternal(primary());
    }

    /**
     * Marks the transaction of the given factory as rollback-only.
     *
     * <p>This is used when an inner operation fails but the exception is handled
     * by user code. The outer transaction must still rollback.</p>
     *
     * @param factory the SessionFactory whose transaction should be marked
     */
    public static void markRollbackOnly(SessionFactory factory) {
        markRollbackOnlyInternal(holders().get(factory));
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
     * Checks whether the given factory has an active transaction on this thread.
     *
     * @param factory the SessionFactory to test
     * @return {@code true} if a transaction is active for {@code factory}
     */
    public static boolean inTransaction(SessionFactory factory) {
        Holder h = holders().get(factory);
        return h != null && h.depth > 0;
    }

    /**
     * Checks whether any factory has an active transaction on this thread.
     *
     * @return {@code true} if at least one transaction is active
     */
    public static boolean inTransaction() {
        for (Holder h : holders().values()) {
            if (h.depth > 0) return true;
        }
        return false;
    }

    /**
     * Closes the current Session when no transaction is active.
     *
     * <p>Used by operations that do not require explicit transactions.</p>
     */
    public static void close() {
        for (Holder h : new ArrayList<>(holders().values())) {
            if (h.depth == 0) closeIfOwner(h);
        }
    }

    /**
     * Test-only cleanup method.
     *
     * <p>Clears ThreadLocal state and releases resources after tests.</p>
     */
    static void reset() {
        for (Holder h : new ArrayList<>(holders().values())) {
            try {
                if (h.tx != null && h.tx.isActive()) {
                    h.tx.rollback();
                }
            } catch (RuntimeException ignored) {
                // Ignore cleanup failures during tests.
            }
            closeIfOwner(h);
        }
    }

    /**
     * Selects the holder the factory-less convenience methods operate on: the most
     * recently begun active transaction, or the only registered holder.
     */
    private static Holder primary() {
        Map<SessionFactory, Holder> map = holders();
        Holder newest = null;
        for (Holder h : map.values()) {
            if (h.depth <= 0) continue;
            if (newest == null || h.begunAt > newest.begunAt) {
                newest = h;
            }
        }
        if (newest != null) return newest;
        if (map.size() == 1) return map.values().iterator().next();
        return null;
    }

    private static Holder requireHolder(Holder h) {
        if (h == null) {
            throw new HibernateLiteException("No transaction context");
        }
        return h;
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
     * <p>Removes the ThreadLocal reference for that factory to prevent leaks when
     * threads are reused by thread pools.</p>
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
        Map<SessionFactory, Holder> map = holders();
        map.remove(h.factory);
        // Prevent stale context from remaining in reused threads.
        if (map.isEmpty()) {
            CURRENT.remove();
        }
    }

}
