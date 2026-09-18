package me.pfzh.hibernatelite;

import me.pfzh.hibernatelite.transaction.TransactionCallback;

import java.util.List;

/**
 * Hibernate-Lite 唯一门面。
 *
 * <p>用户只依赖此接口。所有 CRUD、事务、逃生舱能力都通过这里暴露。</p>
 */
public interface DataStore {

    // ---------- 读 ----------

    /**
     * 按主键查询。找不到返回 null。
     */
    <T> T find(Class<T> type, Object id);

    // ---------- 写 ----------

    /**
     * 保存实体。ID 为 null → 新增；非 null → 更新（merge）。
     *
     * @return 持久化后的实体（merge 场景下为新实例）
     */
    <T> T save(T entity);

    /**
     * 批量保存。整个操作在一个事务内完成。
     *
     * <p><b>返回值说明</b>：返回列表中的实体因 flush + clear
     * 可能已脱离 Persistence Context。如需继续修改后保存，请重新 find 或 merge。</p>
     *
     * @return 持久化后的实体列表（顺序与入参一致）
     */
    <T> List<T> saveAll(List<T> entities);

    /**
     * 删除实体。
     */
    void delete(Object entity);

    // ---------- 事务 ----------

    /**
     * 在事务中执行回调，返回其结果。
     * <p>抛 RuntimeException → 回滚并原样抛出。</p>
     */
    <T> T transaction(TransactionCallback<T> callback);

    /**
     * 在事务中执行无返回值的操作。
     */
    default void transaction(Runnable work) {
        transaction((TransactionCallback<Void>) () -> {
            work.run();
            return null;
        });
    }

    // ---------- 逃生舱 ----------

    /**
     * 获取底层对象。
     *
     * <p>当前支持的类型：{@code SessionFactory.class}。</p>
     *
     * @throws me.pfzh.hibernatelite.exception.HibernateLiteException 不支持的类型
     *         不支持的类型
     */
    <T> T unwrap(Class<T> type);
}