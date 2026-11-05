package me.pfzh.hibernatelite.internal;

import jakarta.persistence.PersistenceException;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
     * Maximum number of entities processed before flushing the current batch.
     *
     * <p>When {@code saveAll} joins a caller transaction, only the saved
     * entities are detached; when it owns the transaction, the temporary
     * persistence context is cleared. Both keep memory bounded, but the first
     * form must not evict unrelated entities loaded by the caller.</p>
     */
    private static final int BATCH_SIZE = 50;

    /**
     * Provides cached metadata information for entity classes.
     */
    private final MetadataRegistry metadataRegistry;

    /**
     * Shared transaction wrapper.
     */
    private final TransactionTemplate txTemplate;

    /**
     * Creates a CRUD executor.
     *
     * @param metadataRegistry cached entity metadata; must not be {@code null}
     * @param txTemplate       shared transaction wrapper; must not be {@code null}
     */
    public CrudExecutor(MetadataRegistry metadataRegistry,
                        TransactionTemplate txTemplate) {
        if (metadataRegistry == null) {
            throw new IllegalArgumentException("MetadataRegistry cannot be null");
        }
        if (txTemplate == null) {
            throw new IllegalArgumentException("TransactionTemplate cannot be null");
        }
        this.metadataRegistry = metadataRegistry;
        this.txTemplate = txTemplate;
    }

    /**
     * Finds an entity by primary key.
     *
     * <p>The Session is obtained from the current thread context.
     * If no transaction exists, a temporary transaction is created
     * automatically.</p>
     *
     * <p><b>Note:</b> read-only queries currently also start a short-lived
     * transaction. This is correct but slightly heavier than a pure
     * read-only connection. A dedicated read-only path may be added later.</p>
     */
    public <T> T find(Class<T> type, Object id) {
        requireNonNull(type, "entity type");
        requireNonNull(id, "primary key");
        return wrap("find", () -> txTemplate.execute(session ->
                session.find(type, id)));
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
     *     <li>ID is not null and the entity has a generated identifier
     *     ({@code @GeneratedValue}):
     *     execute {@code merge()} for a detached entity.
     *     <b>Note:</b> {@code merge()} may issue an UPDATE if a row with
     *     that identifier already exists. Do not pass an entity with a
     *     manually assigned identifier unless you intend an update.</li>
     *
     *     <li>ID is not null and the entity uses a business identifier
     *     (no {@code @GeneratedValue}):
     *     automatic save semantics are not supported, and a
     *     {@link HibernateLiteException} is thrown.</li>
     * </ul>
     *
     * <p>If you need explicit control, use {@code persist} / {@code merge}
     * semantics directly through a Hibernate {@code Session}.</p>
     */
    public <T> T save(T entity) {
        requireNonNull(entity, "entity");
        return wrap("save", () -> txTemplate.execute(session ->
                doSave(session, entity)));
    }

    /**
     * Saves multiple entities.
     *
     * <p>Entities are processed in batches. Every {@link #BATCH_SIZE} entities
     * Hibernate is flushed, so memory stays bounded.</p>
     *
     * <p>When {@code saveAll} starts its own transaction, the flushed batch is
     * removed with {@code clear()}. When it joins a transaction opened by the
     * caller, only the entities written by this batch are detached: clearing the
     * whole persistence context would silently drop changes that the caller made
     * to other managed entities.</p>
     *
     * <p><b>Warning:</b> after each batch boundary the entities saved in that
     * batch become <b>detached</b>. Do not rely on lazy associations of the
     * returned entities after calling this method.</p>
     *
     * @param entities entities to save; must not be {@code null}
     * @param <T>      entity type
     * @return the saved entities, in the same order as the input
     */
    public <T> List<T> saveAll(List<T> entities) {
        requireNonNull(entities, "entity list");
        if (entities.isEmpty()) return entities;
        boolean joiningExistingTransaction = txTemplate.inTransaction();
        return wrap("saveAll", () -> txTemplate.execute(session ->
                doSaveAll(session, entities, joiningExistingTransaction)));
    }

    /**
     * Deletes an entity.
     *
     * <p>If the entity is already managed by Hibernate,
     * it can be removed directly.</p>
     *
     * <p>If the entity is detached, the ID is extracted and the corresponding
     * database record is loaded before deletion. For a versioned entity the
     * version of the detached snapshot is compared with the current row; a
     * mismatch is rejected with {@link HibernateLiteException} instead of
     * silently deleting data that changed after the snapshot was taken.</p>
     */
    public void delete(Object entity) {
        requireNonNull(entity, "entity");
        wrap("delete", () -> {
            txTemplate.execute(session -> {
                /*
                 * Managed entity:
                 * directly remove from persistence context. Hibernate performs
                 * the version check during flush for this path.
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

                Object managed = session.get(meta.getEntityClass(), id);

                /*
                 * No corresponding record.
                 * Delete is treated as idempotent.
                 */
                if (managed == null) {
                    return null;
                }

                /*
                 * session.get() always returns the current row, so without an
                 * explicit check a stale detached snapshot would delete a row
                 * that was modified after the snapshot was read. A null version
                 * (for example an uninitialised proxy) is not comparable and is
                 * allowed through, matching the previous idempotent behaviour.
                 */
                if (meta.hasVersion()) {
                    Object detachedVersion = meta.getVersion(entity);
                    Object currentVersion = meta.getVersion(managed);
                    if (detachedVersion != null
                            && !Objects.equals(detachedVersion, currentVersion)) {
                        throw new HibernateLiteException(
                                "Stale entity: " + entity.getClass().getName()
                                        + " has version " + detachedVersion
                                        + " but the database row has version " + currentVersion
                                        + "; reload the entity before deleting it");
                    }
                }

                session.remove(managed);
                return null;
            });
            return null;
        });
    }

    /**
     * Converts persistence exceptions into library-specific exceptions.
     *
     * <p>This prevents users from depending directly on Hibernate APIs.
     * {@link PersistenceException} is the common supertype of Hibernate
     * exceptions and JPA exceptions such as {@code OptimisticLockException};
     * catching it covers both families.</p>
     */
    private <R> R wrap(String operation, Supplier<R> action) {
        try {
            return action.get();
        } catch (HibernateLiteException e) {
            throw e;
        } catch (PersistenceException e) {
            throw new HibernateLiteException(operation + " failed", e);
        }
    }

    /**
     * Saves an entity within an existing Hibernate session.
     *
     * <p>The persistence strategy depends on the identifier state:</p>
     * <ul>
     *     <li>ID is {@code null}: {@link Session#persist(Object)}.</li>
     *     <li>ID is not {@code null} and the entity has a generated identifier:
     *     {@link Session#merge(Object)}. This may trigger an UPDATE.</li>
     *     <li>ID is not {@code null} and the entity uses a business identifier:
     *     a {@link HibernateLiteException} is thrown, because new vs. existing
     *     cannot be determined from the identifier alone.</li>
     * </ul>
     *
     * @param session current Hibernate session
     * @param entity  entity instance to save
     * @param <T>     entity type
     * @return the persisted or merged entity
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
                    "Cannot determine save semantics for entity with business identifier "
                            + "(no @GeneratedValue): " + entity.getClass().getName()
                            + ". Use Session#persist or Session#merge explicitly.");
        }

        return (T) session.merge(entity);
    }

    /**
     * Batch save implementation.
     *
     * <p>{@code flush()} sends the SQL of the current batch to the database.
     * When the caller already had a transaction, only the entities written by
     * this batch are detached; clearing the whole persistence context would
     * drop changes that the caller made to other managed entities.</p>
     *
     * @param joiningExistingTransaction whether {@code saveAll} joins a
     *                                   transaction opened by the caller
     */
    private <T> List<T> doSaveAll(Session session, List<T> entities,
                                  boolean joiningExistingTransaction) {
        List<T> result = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (entity == null) {
                throw new HibernateLiteException("Entity at index " + i + " is null");
            }
            result.add(doSave(session, entity));
            if ((i + 1) % BATCH_SIZE == 0) {
                session.flush();
                if (joiningExistingTransaction) {
                    detachBatch(session, result, i + 1);
                } else {
                    session.clear();
                }
            }
        }
        return result;
    }

    /**
     * Detaches the entities that were saved in the batch ending at
     * {@code endExclusive}.
     *
     * <p>Using {@link Session#detach(Object)} instead of
     * {@link Session#clear()} is intentional: {@code clear()} would also evict
     * entities loaded by the caller, whose subsequent changes would then be
     * silently ignored.</p>
     */
    private static void detachBatch(Session session, List<?> saved, int endExclusive) {
        int start = Math.max(0, endExclusive - BATCH_SIZE);
        for (int i = start; i < endExclusive; i++) {
            Object entity = saved.get(i);
            if (entity != null) {
                session.detach(entity);
            }
        }
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