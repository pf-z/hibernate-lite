package me.pfzh.hibernatelite.transaction;

/**
 * Callback executed inside a transaction.
 *
 * <p>The callback describes a business operation only.
 * Transaction lifecycle (begin, commit, rollback) is managed
 * externally by {@link TransactionManager}.</p>
 *
 * <p>If this callback throws a {@link RuntimeException},
 * the surrounding transaction is rolled back and the exception
 * is propagated to the caller unchanged.</p>
 *
 * @param <T> type of the result returned by the callback
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    /**
     * Executes the transactional operation.
     *
     * @return the result of the operation; may be {@code null}
     * @throws RuntimeException if the operation fails;
     *                          this triggers a rollback
     */
    T execute();

}