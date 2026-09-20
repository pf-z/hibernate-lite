/**
 * Entity metadata cache.
 *
 * <p>Provides lightweight reflection-based metadata for entity
 * classes, primarily used to locate {@code @Id} fields and to
 * validate query field names.</p>
 *
 * <ul>
 *     <li>{@code MetadataRegistry} — per-instance, thread-safe,
 *     lazily populated cache of {@code EntityMeta}.</li>
 *     <li>{@code EntityMeta} — cached metadata for a single entity
 *     class: id field, field map, and generated-id flag.</li>
 * </ul>
 *
 * <p>This is intentionally not a full ORM metadata system. Only the
 * information required by Hibernate-Lite itself is cached.</p>
 */
package me.pfzh.hibernatelite.metadata;