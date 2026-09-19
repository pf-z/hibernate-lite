package me.pfzh.hibernatelite.transaction;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.internal.SessionContext;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;

import java.util.Objects;

/**
 * Programmatic transaction manager.
 *
 * <p>Provides the simplest transaction semantics: the user logic is
 * executed inside the callback, committed on normal return, and rolled
 * back when a {@link RuntimeException} is thrown.</p>
 *
 * <p><b>Nesting behavior (REQUIRED semantics):</b> if the current thread
 * is already inside a transaction, calling {@link #execute} does not start
 * a new one. The callback simply participates in the outer transaction.
 * Only the outermost transaction performs the real commit or rollback.</p>
 *
 * <p><b>Rollback propagation:</b> when a nested callback fails, the outer
 * transaction is not rolled back immediately. Instead, it is marked as
 * rollback-only, and the outermost commit will roll back and throw
 * {@link HibernateLiteException}.</p>
 *
 * <p><b>Exception propagation:</b> only {@link RuntimeException} is caught.
 * The original exception is rethrown unchanged after rollback;
 * it is never wrapped.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class TransactionManager {

    private final SessionFactoryHolder factoryHolder;

    /**
     * Creates a transaction manager.
     *
     * @param factoryHolder provides access to the global SessionFactory;
     *                      must not be {@code null}
     */
    public TransactionManager(SessionFactoryHolder factoryHolder) {
        this.factoryHolder = Objects.requireNonNull(factoryHolder,
                "factoryHolder cannot be null");
    }

    /**
     * Executes the callback inside a transaction.
     *
     * <p>If no transaction is active on the current thread, a new one
     * is started and committed (or rolled back on failure).</p>
     *
     * <p>If a transaction is already active, the callback participates
     * in it. On failure, the outer transaction is marked as
     * rollback-only instead of being rolled back immediately.</p>
     *
     * @param callback the transactional operation; must not be {@code null}
     * @param <T>      callback result type
     * @return the callback result; may be {@code null}
     *
     * @throws IllegalArgumentException if {@code callback} is {@code null}
     * @throws RuntimeException if the callback fails; the original
     *                          exception is rethrown unchanged
     */
    public <T> T execute(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback cannot be null");

        boolean autoTx = !SessionContext.inTransaction();
        if (autoTx) {
            SessionContext.begin(factoryHolder.get());
        }
        try {
            T result = callback.execute();
            if (autoTx) {
                SessionContext.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (autoTx) {
                SessionContext.rollback();
            } else {
                SessionContext.markRollbackOnly();
            }
            throw e;
        }
    }

}
