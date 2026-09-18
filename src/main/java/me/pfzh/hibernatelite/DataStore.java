package me.pfzh.hibernatelite;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.transaction.TransactionCallback;

import java.util.List;

/**
 * Main facade interface of Hibernate-Lite.
 *
 * <p>
 * This is the only API that users need to depend on.
 * It hides Hibernate internal concepts such as Session,
 * Transaction, and SessionFactory.
 * </p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public interface DataStore {

    /**
     * Finds an entity by its primary key.
     *
     * <p>
     * If no entity exists with the given identifier,
     * this method returns {@code null}.
     * </p>
     *
     * @param type entity class
     * @param id primary key value
     * @return found entity or null
     */
    <T> T find(Class<T> type, Object id);

    /**
     * Saves an entity.
     *
     * <p>
     * The behavior depends on the entity identifier:
     * </p>
     *
     * <ul>
     *     <li>
     *     ID is {@code null}: create a new database record.
     *     </li>
     *
     *     <li>
     *     ID is not {@code null}: update existing entity
     *     through Hibernate merge operation.
     *     </li>
     * </ul>
     *
     * <p>
     * Note:
     * Hibernate {@code merge()} returns a managed copy.
     * Therefore, the returned object may not be the same
     * instance as the input object.
     * </p>
     *
     * @param entity entity to persist
     * @return persisted entity
     */
    <T> T save(T entity);

    /**
     * Saves multiple entities in one transaction.
     *
     * <p>
     * All entities are persisted within a single transaction
     * to improve consistency and performance.
     * </p>
     *
     * <p>
     * During batch processing, the persistence context may be
     * flushed and cleared.
     * Therefore returned objects may no longer be managed
     * by Hibernate.
     * </p>
     *
     * <p>
     * If further modification is required,
     * reload the entity through {@link #find(Class, Object)}
     * or explicitly merge it again.
     * </p>
     *
     * @param entities entities to save
     * @return persisted entities in the same order
     */
    <T> List<T> saveAll(List<T> entities);

    /**
     * Deletes an entity from database.
     *
     * <p>
     * The operation is executed within the current transaction.
     * </p>
     *
     * @param entity entity to remove
     */
    void delete(Object entity);


    /**
     * Executes an operation inside a transaction.
     *
     * <p>
     * Transaction lifecycle is managed automatically:
     * </p>
     *
     * <ul>
     *     <li>Begin transaction before callback execution</li>
     *     <li>Commit after successful execution</li>
     *     <li>Rollback when RuntimeException occurs</li>
     * </ul>
     *
     * <p>
     * Runtime exceptions are not wrapped or hidden;
     * they are propagated to the caller after rollback.
     * </p>
     *
     * @param callback transactional operation
     * @return callback result
     */
    <T> T transaction(TransactionCallback<T> callback);

    /**
     * Executes a transaction without returning a value.
     *
     * <p>
     * This is a convenience method that adapts
     * {@link Runnable} into {@link TransactionCallback}.
     * </p>
     *
     * @param work transactional operation
     */
    default void transaction(Runnable work) {
        transaction((TransactionCallback<Void>) () -> {
            work.run();
            return null;
        });
    }

    /**
     * Provides access to underlying Hibernate objects.
     *
     * <p>
     * This method is an escape hatch for advanced users
     * who need features not directly exposed by Hibernate-Lite.
     * </p>
     *
     * <p>
     * Currently supported:
     * </p>
     *
     * <ul>
     *     <li>{@code SessionFactory.class}</li>
     * </ul>
     *
     * @param type requested underlying type
     * @param <T> requested type
     * @return underlying Hibernate object
     *
     * @throws HibernateLiteException
     * if the requested type is unsupported
     */
    <T> T unwrap(Class<T> type);

}