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
public interface DataStore extends AutoCloseable {

    /**
     * Finds an entity by its primary key.
     *
     * <p>
     * If no entity exists with the given identifier,
     * this method returns {@code null}.
     * </p>
     *
     * <p>
     * If no transaction is active, a short-lived transaction is
     * started automatically for this read operation.
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
     *     ID is {@code null}: create a new database record
     *     using Hibernate {@code persist()}.
     *     </li>
     *
     *     <li>
     *     ID is not {@code null} and the entity has a generated identifier
     *     ({@code @GeneratedValue}): update the existing entity
     *     through Hibernate {@code merge()}.
     *     </li>
     *
     *     <li>
     *     ID is not {@code null} and the entity uses a business identifier
     *     (no {@code @GeneratedValue}): automatic save semantics are not
     *     supported. A {@link HibernateLiteException} is thrown, because
     *     the identifier alone cannot distinguish a new entity from an
     *     existing one.
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
     * <p><b>Warning:</b> for entities with a generated identifier,
     * a non-null ID is treated as a detached entity and merged.
     * If the ID was assigned manually (not by Hibernate), this may
     * accidentally UPDATE an existing row instead of INSERTing.
     * </p>
     *
     * @param entity entity to persist
     * @return persisted entity
     * @throws HibernateLiteException if automatic save semantics cannot
     *                                be determined (e.g. business identifier)
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
     * @return persisted entities in the same order; entities after a
     *         batch boundary are detached
     */
    <T> List<T> saveAll(List<T> entities);

    /**
     * Deletes an entity from database.
     *
     * <p>
     * The operation is executed within the current transaction.
     * </p>
     *
     * <p>
     * Deletion is idempotent: if no matching record exists,
     * the method returns silently.
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
     * @param type requested underlying type; must not be {@code null}
     * @param <T> requested type
     * @return underlying Hibernate object
     *
     * @throws IllegalArgumentException if {@code type} is {@code null}
     * @throws HibernateLiteException
     * if the requested type is unsupported
     */
    <T> T unwrap(Class<T> type);

    /**
     * Releases the underlying Hibernate SessionFactory and related resources.
     *
     * <p>After calling this method, the DataStore must not be used again.</p>
     */
    @Override
    void close();

}