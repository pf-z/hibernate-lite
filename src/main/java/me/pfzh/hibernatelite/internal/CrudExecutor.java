package me.pfzh.hibernatelite.internal;

import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Executes CRUD operations using Hibernate Session.
 *
 * <p>This class is responsible for:
 * <ul>
 *     <li>Entity persistence operations.</li>
 *     <li>Automatic transaction handling when no transaction exists.</li>
 *     <li>Exception conversion from Hibernate exceptions to library exceptions.</li>
 *     <li>Batch persistence optimization.</li>
 * </ul>
 *
 * <p>The class itself is stateless and thread-safe.
 * Thread-specific Session management is delegated to {@link SessionContext}.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class CrudExecutor {

    /**
     * Maximum number of entities processed before flushing
     * and clearing Hibernate persistence context.
     *
     * <p>This prevents memory growth during large batch operations.</p>
     */
    private static final int BATCH_SIZE = 50;

    /**
     * Provides access to the global Hibernate SessionFactory.
     */
    private final SessionFactoryHolder factoryHolder;

    /**
     * Provides cached metadata information for entity classes.
     */
    private final MetadataRegistry metadataRegistry;

    /**
     * Creates a CRUD executor.
     */
    public CrudExecutor(SessionFactoryHolder factoryHolder, MetadataRegistry metadataRegistry) {
        if (factoryHolder == null) {
            throw new IllegalArgumentException("SessionFactoryHolder cannot be null");
        }
        if (metadataRegistry == null) {
            throw new IllegalArgumentException("MetadataRegistry cannot be null");
        }
        this.factoryHolder = factoryHolder;
        this.metadataRegistry = metadataRegistry;
    }

    /**
     * Finds an entity by primary key.
     *
     * <p>The Session is obtained from the current thread context.
     * If no transaction exists, a temporary transaction is created
     * automatically.</p>
     */
    public <T> T find(Class<T> type, Object id) {
        requireNonNull(type, "entity type");
        requireNonNull(id, "primary key");
        return wrap("find", () -> execute(() ->
                SessionContext.current(factoryHolder.get()).find(type, id)));
    }

    /**
     * Saves an entity.
     *
     * <p>The operation behaves differently according to entity ID:</p>
     *
     * <ul>
     *     <li>ID is null:
     *     execute {@code persist()} for a new entity.</li>
     *
     *     <li>ID exists:
     *     execute {@code merge()} for detached entity.</li>
     * </ul>
     */
    public <T> T save(T entity) {
        requireNonNull(entity, "entity");
        return wrap("save", () -> execute(() ->
                doSave(SessionContext.current(factoryHolder.get()), entity)));
    }

    /**
     * Saves multiple entities.
     *
     * <p>Entities are processed in batches.
     * Every {@link #BATCH_SIZE} entities Hibernate is flushed
     * and persistence context is cleared.</p>
     */
    public <T> List<T> saveAll(List<T> entities) {
        requireNonNull(entities, "entity list");
        if (entities.isEmpty()) return entities;
        return wrap("saveAll", () -> execute(() ->
                doSaveAll(SessionContext.current(factoryHolder.get()), entities)));
    }

    /**
     * Deletes an entity.
     *
     * <p>If the entity is already managed by Hibernate,
     * it can be removed directly.</p>
     *
     * <p>If the entity is detached,
     * the ID is extracted and the corresponding database record
     * is loaded before deletion.</p>
     */
    public void delete(Object entity) {
        requireNonNull(entity, "entity");
        wrap("delete", () -> {
            execute(() -> {
                Session session = SessionContext.current(factoryHolder.get());

                /*
                 * Managed entity:
                 * directly remove from persistence context.
                 */
                if (session.contains(entity)) {
                    session.remove(entity);
                    return null;
                }

                /*
                 * Detached entity:
                 * find its identifier first.
                 */
                EntityMeta meta = metadataRegistry.get(entity.getClass());
                Object id = meta.getId(entity);
                if (id == null) {
                    throw new HibernateLiteException(
                            "Entity ID is null: " + entity.getClass().getName());
                }

                Object managed = session.get(entity.getClass(), id);

                /*
                 * No corresponding record.
                 * Delete is treated as idempotent.
                 */
                if (managed == null) {
                    return null;
                }
                session.remove(managed);
                return null;
            });
            return null;
        });
    }

    /**
     * Executes an operation with automatic transaction handling.
     *
     * <p>If caller already started a transaction,
     * this method participates in that transaction.</p>
     *
     * <p>If no transaction exists:
     * <ul>
     *     <li>begin transaction</li>
     *     <li>execute operation</li>
     *     <li>commit or rollback</li>
     * </ul>
     */
    private <R> R execute(Supplier<R> action) {
        SessionFactory sf = factoryHolder.get();

        /*
         * Only create transaction when user code
         * does not already run inside one.
         */
        boolean autoTx = !SessionContext.inTransaction();
        if (autoTx) SessionContext.begin(sf);
        try {
            R result = action.get();
            if (autoTx) SessionContext.commit();
            return result;
        } catch (RuntimeException e) {
            /*
             * If this method owns the transaction,
             * rollback immediately.
             *
             * Otherwise mark the outer transaction as rollback-only.
             */
            if (autoTx) {
                SessionContext.rollback();
            } else {
                SessionContext.markRollbackOnly();
            }
            throw e;
        }
    }

    /**
     * Converts Hibernate exceptions into library-specific exceptions.
     *
     * <p>This prevents users from depending directly on Hibernate APIs.</p>
     */
    private <R> R wrap(String operation, Supplier<R> action) {
        try {
            return action.get();
        } catch (HibernateLiteException e) {
            throw e;
        } catch (HibernateException e) {
            throw new HibernateLiteException(operation + " failed", e);
        }
    }

    /**
     * Saves an entity within an existing Hibernate session.
     *
     * <p>The persistence strategy depends on the identifier state:</p>
     * <ul>
     *     <li>
     *         If the identifier is {@code null}, the entity is treated as a new
     *         entity and persisted using {@link Session#persist(Object)}.
     *     </li>
     *     <li>
     *         If the entity uses {@link jakarta.persistence.GeneratedValue} and
     *         the identifier is not {@code null}, the entity is treated as an
     *         existing entity and merged using {@link Session#merge(Object)}.
     *     </li>
     *     <li>
     *         For entities with business identifiers (without
     *         {@code @GeneratedValue}), the identifier value alone cannot
     *         distinguish between a new entity and an existing entity.
     *         Therefore, automatic save semantics are not supported.
     *     </li>
     * </ul>
     *
     * <p>This method intentionally avoids guessing persistence state for
     * business-key entities to prevent unintended insert/update behavior.</p>
     *
     * @param session current Hibernate session
     * @param entity entity instance to save
     * @param <T> entity type
     *
     * @return the persisted or merged entity
     *
     * @throws HibernateLiteException if the entity uses a business identifier
     *                                and automatic save semantics cannot be
     *                                determined
     */
    @SuppressWarnings("unchecked")
    private <T> T doSave(Session session, T entity) {
        EntityMeta meta = metadataRegistry.get(entity.getClass());
        Object id = meta.getId(entity);
        if (id == null) {
            session.persist(entity);
            return entity;
        }

        // For business identifiers, a non-null ID does not indicate
        // whether the entity is new or already persistent.
        if (!meta.hasGeneratedId()) {
            throw new HibernateLiteException(
                    "Business identifier entity (without @GeneratedValue) does not support automatic save semantics: "
                            + entity.getClass().getName()
                            + ". Please explicitly choose persist or merge semantics (not supported in the current version).");
        }

        return (T) session.merge(entity);
    }

    /**
     * Batch save implementation.
     *
     * <p>flush() sends SQL statements to database.</p>
     *
     * <p>clear() removes managed objects from Hibernate
     * first-level cache to avoid excessive memory usage.</p>
     */
    private <T> List<T> doSaveAll(Session session, List<T> entities) {
        List<T> result = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (entity == null) {
                throw new HibernateLiteException("Entity at index " + i + " is null");
            }
            result.add(doSave(session, entity));
            if ((i + 1) % BATCH_SIZE == 0) {
                session.flush();
                session.clear();
            }
        }
        return result;
    }

    /**
     * Validates required parameters.
     */
    private static void requireNonNull(Object v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

}