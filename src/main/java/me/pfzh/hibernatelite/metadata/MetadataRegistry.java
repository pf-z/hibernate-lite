package me.pfzh.hibernatelite.metadata;

import me.pfzh.hibernatelite.exception.HibernateLiteException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for cached entity metadata.
 *
 * <p>{@code MetadataRegistry} maintains a mapping between entity classes
 * and their corresponding {@link EntityMeta} instances.</p>
 *
 * <p>Metadata is created lazily on the first access and reused afterwards,
 * avoiding repeated reflection scanning during runtime operations.</p>
 *
 * <p>The registry is thread-safe and can be shared across multiple
 * CRUD/query operations.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class MetadataRegistry {

    /**
     * Cached metadata indexed by entity class.
     *
     * <p>{@link ConcurrentHashMap} is used to support concurrent access
     * from multiple threads.</p>
     */
    private final Map<Class<?>, EntityMeta> cache = new ConcurrentHashMap<>();

    /**
     * Returns metadata for the specified entity class.
     *
     * <p>If the metadata does not exist in the cache, a new
     * {@link EntityMeta} instance is created by performing reflection
     * scanning.</p>
     *
     * <p>If the entity class is invalid (no {@code @Id}, or multiple
     * {@code @Id} fields), the construction of {@link EntityMeta} will
     * throw a {@link HibernateLiteException}. In that case, nothing is
     * cached, and the next call will retry the reflection scan.</p>
     *
     * @param entityClass entity class; must not be {@code null}
     * @return cached entity metadata
     *
     * @throws IllegalArgumentException if {@code entityClass} is {@code null}
     * @throws HibernateLiteException if the entity class has no {@code @Id}
     *                                or declares multiple {@code @Id} fields
     */
    public EntityMeta get(Class<?> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass cannot be null");
        }
        return cache.computeIfAbsent(entityClass, EntityMeta::new);
    }

}