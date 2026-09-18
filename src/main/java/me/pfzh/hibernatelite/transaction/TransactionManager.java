package me.pfzh.hibernatelite.transaction;

import me.pfzh.hibernatelite.internal.SessionContext;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;

import java.util.Objects;

/**
 * 编程式事务管理器。
 *
 * <p>提供最简单的事务语义：在回调内执行用户逻辑，
 * 正常返回则提交，抛异常则回滚。</p>
 *
 * <p><b>嵌套行为</b>：若当前线程已在事务中，再次调用 {@code execute}
 * 不会开启新事务，而是复用外层事务（REQUIRED 语义）。这是由
 * {@link SessionContext} 的 depth 计数实现的，本类不感知。</p>
 *
 * <p><b>异常传播</b>：只捕获 {@link RuntimeException}。用户抛出什么，
 * 回滚后原样抛回，不包装（除非回滚本身失败）。</p>
 */
public final class TransactionManager {

    private final SessionFactoryHolder factoryHolder;

    public TransactionManager(SessionFactoryHolder factoryHolder) {
        this.factoryHolder = Objects.requireNonNull(factoryHolder,
                "SessionFactoryHolder 不能为 null");
    }

    /**
     * 在事务中执行回调。
     *
     * <p>正常返回 → 提交。</p>
     * <p>抛 {@link RuntimeException} → 回滚并原样抛出。</p>
     *
     * @param callback 事务内逻辑，不可为 null
     */
    public <T> T execute(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "TransactionCallback 不能为 null");
        SessionContext.begin(factoryHolder.get());
        try {
            T result = callback.execute();
            SessionContext.commit();
            return result;
        } catch (RuntimeException e) {
            SessionContext.rollback();
            throw e;
        }
    }

}
