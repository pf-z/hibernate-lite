package me.pfzh.hibernatelite.internal;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Reusable transaction wrapper for internal executors.
 *
 * <p>This class centralizes transaction boundary handling so that
 * {@link CrudExecutor} and future query executors do not duplicate
 * begin / commit / rollback logic.</p>
 *
 * <p><b>Transaction semantics:</b></p>
 * <ul>
 *     <li>If the caller is already inside a transaction, the operation
 *     participates in that transaction and is <b>not</b> committed here.
 *     The outermost caller is responsible for commit or rollback.</li>
 *     <li>If no transaction exists, a temporary transaction is created,
 *     committed on success, and rolled back on failure.</li>
 * </ul>
 *
 * <p><b>Exception semantics:</b></p>
 * <ul>
 *     <li>When this template owns the transaction: rollback immediately
 *     and rethrow the original exception.</li>
 *     <li>When the transaction belongs to an outer scope: mark it as
 *     rollback-only, so the outermost caller can decide when to roll
 *     back. This preserves the original exception and avoids
 *     premature rollback while allowing user code to observe the
 *     failure.</li>
 * </ul>
 *
 * <p><b>Note on commit failure:</b> if {@link SessionContext#commit()}
 * itself fails, it performs internal cleanup before propagating the
 * exception. The rollback call in the catch block then becomes a no-op.
 * It is kept here for the case where the action (not the commit) fails.</p>
 *
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
public final class TransactionTemplate {

    /**
     * Provides access to the global Hibernate SessionFactory.
     */
    private final SessionFactoryHolder factoryHolder;

    /**
     * Creates a transaction template bound to a session factory holder.
     *
     * @param factoryHolder holder of the global {@link SessionFactory};
     *                      must not be {@code null}
     */
    public TransactionTemplate(SessionFactoryHolder factoryHolder) {
        this.factoryHolder = Objects.requireNonNull(
                factoryHolder, "SessionFactoryHolder cannot be null");
    }

    /**
     * Executes an operation inside a transaction.
     *
     * <p>The provided function receives the current thread's Hibernate
     * {@link Session}. The returned value is passed back to the caller.</p>
     *
     * @param action operation to execute; must not be {@code null}
     * @param <R>    result type
     * @return the value returned by {@code action}
     */
    public <R> R execute(Function<Session, R> action) {
        Objects.requireNonNull(action, "action cannot be null");

        SessionFactory sf = factoryHolder.get();

        /*
         * Only create a transaction when user code
         * does not already run inside one.
         */
        boolean autoTx = !SessionContext.inTransaction();
        if (autoTx) SessionContext.begin(sf);

        try {
            R result = action.apply(SessionContext.current(sf));
            if (autoTx) SessionContext.commit();
            return result;
        } catch (RuntimeException e) {
            /*
             * If this template owns the transaction, rollback immediately.
             * Otherwise mark the outer transaction as rollback-only.
             *
             * Note: if the exception originated from SessionContext.commit(),
             * the context has already been cleaned up internally, and the
             * rollback() call below is a no-op. It is kept here to cover
             * failures raised by the action itself.
             */
            if (autoTx) {
                SessionContext.rollback();
            } else {
                SessionContext.markRollbackOnly();
            }
            throw e;
        }
    }

}