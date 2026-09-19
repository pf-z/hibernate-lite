package me.pfzh.hibernatelite.metadata;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Cached metadata information for an entity class.
 *
 * <p>{@code EntityMeta} stores the information required by Hibernate-Lite
 * to operate on an entity, including the identifier field, entity fields,
 * and identifier generation strategy.</p>
 *
 * <p>Instances are created and cached by {@link MetadataRegistry}.
 * Reflection scanning is performed only once for each entity class,
 * avoiding repeated reflection overhead during CRUD operations.</p>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *     <li>Only field access is supported. {@code @Id} must be declared
 *     on a field, not on a getter method.</li>
 *     <li>Only single-field {@code @Id} is supported.
 *     {@code @EmbeddedId} and composite keys are not supported.</li>
 * </ul>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class EntityMeta {

    // Entity class.
    private final Class<?> entityClass;

    // Field annotated with {@link Id}.
    private final Field idField;

    // Whether the identifier is generated automatically.
    private final boolean hasGeneratedId;

    /**
     * Cached entity fields.
     *
     * <p>The key is the Java field name and the value is the corresponding
     * {@link Field} instance.</p>
     */
    private final Map<String, Field> fields;

    /**
     * Creates entity metadata.
     *
     * <p>This constructor has package-private visibility and is only called
     * by {@link MetadataRegistry} to ensure centralized metadata management.</p>
     *
     * @param entityClass entity class
     */
    EntityMeta(Class<?> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass cannot be null");
        }
        this.entityClass = entityClass;
        this.idField = findIdField(entityClass);
        this.hasGeneratedId = idField.isAnnotationPresent(GeneratedValue.class);
        this.fields = Collections.unmodifiableMap(scanFields(entityClass));
    }

    /**
     * Returns the entity class.
     *
     * @return entity class
     */
    public Class<?> getEntityClass() {
        return entityClass;
    }

    /**
     * Retrieves the identifier value from an entity instance.
     *
     * <p>The cached identifier field is used directly to avoid repeated
     * reflection scanning.</p>
     *
     * @param entity entity instance; must not be {@code null} and must be
     *               an instance of the entity class
     * @return identifier value
     *
     * @throws IllegalArgumentException if the entity is {@code null}
     *                                  or not an instance of the entity class
     * @throws HibernateLiteException if reflection access fails
     */
    public Object getId(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        if (!entityClass.isInstance(entity)) {
            throw new IllegalArgumentException(
                    "Invalid entity type: expected " + entityClass.getName()
                            + ", but got " + entity.getClass().getName());
        }
        try {
            return idField.get(entity);
        } catch (IllegalAccessException e) {
            throw new HibernateLiteException(
                    "Failed to read @Id field: " + entityClass.getName() + "#" + idField.getName(), e);
        }
    }

    /**
     * Checks whether this entity uses a generated identifier.
     *
     * <p>For generated identifiers:</p>
     * <pre>
     * id == null     -> new entity
     * id != null     -> persisted entity
     * </pre>
     *
     * <p>For business identifiers, the identifier value alone cannot
     * determine whether an entity has already been persisted.</p>
     *
     * @return {@code true} if the entity uses {@link GeneratedValue}
     */
    public boolean hasGeneratedId() {
        return hasGeneratedId;
    }

    /**
     * Returns a field by its Java field name.
     *
     * <p>This method is mainly used for field validation and future
     * extensions such as auditing or automatic field population.</p>
     *
     * <p>Only non-static, non-synthetic fields are included.
     * Fields declared in superclasses are also visible.</p>
     *
     * @param name field name; if {@code null}, returns {@code null}
     * @return corresponding field, or {@code null} if not found
     */
    public Field getField(String name) {
        if (name == null) return null;
        return fields.get(name);
    }

    /**
     * Finds the identifier field in the entity inheritance hierarchy.
     *
     * <p>The search order is:</p>
     * <ol>
     *     <li>Current class</li>
     *     <li>Superclass</li>
     *     <li>Higher-level superclass</li>
     *     <li>Until {@link Object}</li>
     * </ol>
     *
     * <p>Static and synthetic fields are skipped.
     * If more than one {@code @Id} field is found across the hierarchy,
     * a {@link HibernateLiteException} is thrown.</p>
     *
     * @param clazz entity class
     * @return identifier field
     *
     * @throws HibernateLiteException if no {@code @Id} field is found,
     *                                or if multiple {@code @Id} fields exist
     */
    private static Field findIdField(Class<?> clazz) {
        Field found = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                if (f.isAnnotationPresent(Id.class)) {
                    if (found != null) {
                        throw new HibernateLiteException(
                                "Multiple @Id fields found in entity: " + clazz.getName());
                    }
                    f.setAccessible(true);
                    found = f;
                }
            }
        }
        if (found == null) {
            throw new HibernateLiteException("Entity has no @Id field: " + clazz.getName());
        }
        return found;
    }

    /**
     * Scans all fields of an entity class.
     *
     * <p>The scan includes fields declared in superclasses.
     * Static and synthetic fields are skipped.</p>
     *
     * <p>The scan starts from the child class and proceeds upward,
     * therefore fields declared in subclasses take precedence over
     * fields with the same name declared in parent classes.</p>
     *
     * @param clazz entity class
     * @return mapping from field name to {@link Field}
     */
    private static Map<String, Field> scanFields(Class<?> clazz) {
        Map<String, Field> result = new HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                f.setAccessible(true);
                result.put(f.getName(), f);
            }
        }
        return result;
    }

}
