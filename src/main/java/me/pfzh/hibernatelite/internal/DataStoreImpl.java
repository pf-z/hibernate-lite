package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.transaction.TransactionCallback;
import me.pfzh.hibernatelite.transaction.TransactionManager;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link DataStore}.
 *
 * <p>This class acts as a facade layer.
 * It coordinates internal components but does not contain
 * database or transaction logic.</p>
 *
 * <p>All operations are delegated to:
 * <ul>
 *     <li>{@link CrudExecutor} for CRUD operations.</li>
 *     <li>{@link TransactionManager} for transaction handling.</li>
 *     <li>{@link SessionFactoryHolder} for Hibernate lifecycle.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class DataStoreImpl implements DataStore {

    /**
     * Executes CRUD operations.
     *
     * <p>This component contains the actual Hibernate Session operations,
     * such as find, persist, merge and remove.</p>
     */
    private final CrudExecutor crud;

    /**
     * Controls transaction lifecycle.
     *
     * <p>Responsible for beginning, committing and rolling back
     * Hibernate transactions.</p>
     */
    private final TransactionManager transactionManager;

    /**
     * Holds the global Hibernate SessionFactory.
     *
     * <p>The SessionFactory is a heavyweight, application-wide object,
     * therefore it is created once and shared.</p>
     */
    private final SessionFactoryHolder factoryHolder;

    /**
     * Creates a DataStore implementation with required components.
     *
     * <p>Dependencies are injected from {@code HibernateLite.Builder}
     * instead of being created internally. This keeps the class loosely
     * coupled and easier to test.</p>
     */
    public DataStoreImpl(CrudExecutor crud,
                         TransactionManager transactionManager,
                         SessionFactoryHolder factoryHolder) {
        this.crud = Objects.requireNonNull(crud, "CrudExecutor cannot be null");
        this.transactionManager = Objects.requireNonNull(
                transactionManager, "TransactionManager cannot be null");
        this.factoryHolder = Objects.requireNonNull(
                factoryHolder, "SessionFactoryHolder cannot be null");
    }

    /**
     * Finds an entity by its primary key.
     *
     * <p>The actual Hibernate query operation is delegated
     * to {@link CrudExecutor}.</p>
     */
    @Override
    public <T> T find(Class<T> type, Object id) {
        return crud.find(type, id);
    }

    /**
     * Saves an entity.
     *
     * <p>The implementation details of persist/merge are handled
     * by {@link CrudExecutor}.</p>
     */
    @Override
    public <T> T save(T entity) {
        return crud.save(entity);
    }

    /**
     * Saves multiple entities.
     *
     * <p>The whole batch operation is delegated to {@link CrudExecutor},
     * which controls batching, flushing and persistence context handling.</p>
     */
    @Override
    public <T> List<T> saveAll(List<T> entities) {
        return crud.saveAll(entities);
    }

    /**
     * Deletes an entity.
     *
     * <p>The actual remove operation is performed by {@link CrudExecutor}.</p>
     */
    @Override
    public void delete(Object entity) {
        crud.delete(entity);
    }

    /**
     * Executes a callback inside a transaction.
     *
     * <p>The transaction lifecycle is managed by
     * {@link TransactionManager}.</p>
     *
     * <p>The callback itself only describes the business operation.
     * It does not need to know how transactions are started,
     * committed or rolled back.</p>
     */
    @Override
    public <T> T transaction(TransactionCallback<T> callback) {
        return transactionManager.execute(callback);
    }

    /**
     * Provides access to underlying Hibernate resources.
     *
     * <p>Normally users should use the high-level {@link DataStore} API.
     * This method exists as an escape hatch for advanced cases where
     * direct Hibernate access is required.</p>
     *
     * <p>Currently supported type:</p>
     *
     * <ul>
     *     <li>{@link SessionFactory}</li>
     * </ul>
     *
     * @throws HibernateLiteException if the requested type is unsupported
     */
    @Override
    public <T> T unwrap(Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        if (SessionFactory.class.equals(type)) {
            return type.cast(factoryHolder.get());
        }
        throw new HibernateLiteException("Unsupported unwrap type: " + type.getName());
    }

}