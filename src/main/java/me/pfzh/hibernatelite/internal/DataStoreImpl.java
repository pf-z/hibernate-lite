package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.transaction.TransactionCallback;
import me.pfzh.hibernatelite.transaction.TransactionManager;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Objects;

/**
 * {@link DataStore} 的默认实现。
 *
 * <p>本类只做委托，不含任何业务逻辑。</p>
 *
 * <p>本类无状态、线程安全。</p>
 */
public final class DataStoreImpl implements DataStore {

    private final CrudExecutor crud;
    private final TransactionManager transactionManager;
    private final SessionFactoryHolder factoryHolder;

    public DataStoreImpl(CrudExecutor crud,
                         TransactionManager transactionManager,
                         SessionFactoryHolder factoryHolder) {
        this.crud = Objects.requireNonNull(crud, "CrudExecutor 不能为 null");
        this.transactionManager = Objects.requireNonNull(
                transactionManager, "TransactionManager 不能为 null");
        this.factoryHolder = Objects.requireNonNull(
                factoryHolder, "SessionFactoryHolder 不能为 null");
    }

    // ==================== 读 ====================

    @Override
    public <T> T find(Class<T> type, Object id) {
        return crud.find(type, id);
    }

    // ==================== 写 ====================

    @Override
    public <T> T save(T entity) {
        return crud.save(entity);
    }

    @Override
    public <T> List<T> saveAll(List<T> entities) {
        return crud.saveAll(entities);
    }

    @Override
    public void delete(Object entity) {
        crud.delete(entity);
    }

    // ==================== 事务 ====================

    @Override
    public <T> T transaction(TransactionCallback<T> callback) {
        return transactionManager.execute(callback);
    }

    // ==================== 逃生舱 ====================

    @Override
    public <T> T unwrap(Class<T> type) {
        Objects.requireNonNull(type, "type 不能为 null");
        if (SessionFactory.class.equals(type)) {
            return type.cast(factoryHolder.get());
        }
        throw new HibernateLiteException("不支持的 unwrap 类型: " + type.getName());
    }

}