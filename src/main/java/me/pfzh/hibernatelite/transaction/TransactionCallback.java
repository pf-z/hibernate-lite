package me.pfzh.hibernatelite.transaction;

@FunctionalInterface
public interface TransactionCallback<T> {

    /**
     * 在事务内执行。
     *
     * @return 执行结果
     * @throws RuntimeException 抛出即触发回滚
     */
    T execute();
}