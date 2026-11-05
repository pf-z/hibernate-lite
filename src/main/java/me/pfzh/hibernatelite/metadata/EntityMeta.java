package me.pfzh.hibernatelite.metadata;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
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
 *     <li>Fields declared by a plain (non-{@code @Entity},
 *     non-{@code @MappedSuperclass}) superclass are ignored, matching JPA.</li>
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

    // Optional field annotated with {@link Version}.
    private final Field versionField;

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
        this.versionField = findVersionField(entityClass);
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
        /*
         * A Hibernate proxy does not populate the entity's @Id field until it is
         * initialised, so a plain reflective read would return null and make the
         * entity look "new". The lazy initializer knows the identifier without
         * forcing initialisation.
         */
        if (entity instanceof HibernateProxy proxy) {
            Object identifier = proxy.getHibernateLazyInitializer().getIdentifier();
            if (identifier != null) {
                return identifier;
            }
        }
        try {
            return idField.get(unproxy(entity));
        } catch (IllegalAccessException e) {
            throw new HibernateLiteException(
                    "Failed to read @Id field: " + entityClass.getName() + "#" + idField.getName(), e);
        }
    }

    /**
     * Returns the underlying entity instance for a proxy, without initialising it
     * when the proxy already carries its target.
     */
    private static Object unproxy(Object entity) {
        try {
            return Hibernate.unproxy(entity);
        } catch (RuntimeException e) {
            return entity;
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
     * Checks whether this entity declares a {@link Version} field.
     *
     * <p>When {@code true}, callers are expected to honour optimistic locking:
     * a detached entity whose version differs from the database row is stale.</p>
     *
     * @return {@code true} if a {@code @Version} field is present
     */
    public boolean hasVersion() {
        return versionField != null;
    }

    /**
     * Reads the version value of an entity instance.
     *
     * <p>For an uninitialised Hibernate proxy the version may not be available
     * yet; in that case this method returns {@code null} and callers must skip
     * the optimistic-locking check rather than treating the entity as new.</p>
     *
     * @param entity entity instance
     * @return version value, or {@code null} when the entity has no
     *         {@code @Version} field or the value cannot be read from a proxy
     * @throws IllegalArgumentException if the entity is not an instance of the
     *                                  mapped entity class
     * @throws HibernateLiteException   if reflective access fails
     */
    public Object getVersion(Object entity) {
        if (versionField == null) {
            return null;
        }
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        if (!entityClass.isInstance(entity)) {
            throw new IllegalArgumentException(
                    "Invalid entity type: expected " + entityClass.getName()
                            + ", but got " + entity.getClass().getName());
        }
        try {
            return versionField.get(unproxy(entity));
        } catch (IllegalAccessException e) {
            throw new HibernateLiteException(
                    "Failed to read @Version field: " + entityClass.getName()
                            + "#" + versionField.getName(), e);
        }
    }

    /**
     * Returns a field by its Java field name.
     *
     * <p>This method is mainly used for field validation and future
     * extensions such as auditing or automatic field population.</p>
     *
     * <p>Only persistent fields are included: static, synthetic, Java-transient
     * and {@code @Transient} fields are skipped, as are fields declared by a
     * non-{@code @MappedSuperclass} superclass. Fields of the entity class and
     * its mapped ancestors are visible.</p>
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
            if (!isMappedClass(clazz, c)) continue;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                if (f.isAnnotationPresent(Id.class) && !f.isAnnotationPresent(Transient.class)) {
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
     * Finds the optional {@link Version} field in the entity hierarchy.
     *
     * <p>Only field access is supported, matching the identifier rules. A
     * missing version field is not an error: optimistic locking is optional.</p>
     *
     * @param clazz entity class
     * @return version field, or {@code null} when the entity is not versioned
     * @throws HibernateLiteException if more than one {@code @Version} field is found
     */
    private static Field findVersionField(Class<?> clazz) {
        Field found = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            if (!isMappedClass(clazz, c)) continue;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                if (f.isAnnotationPresent(Version.class) && !f.isAnnotationPresent(Transient.class)) {
                    if (found != null) {
                        throw new HibernateLiteException(
                                "Multiple @Version fields found in entity: " + clazz.getName());
                    }
                    f.setAccessible(true);
                    found = f;
                }
            }
        }
        return found;
    }

    /**
     * Checks whether a class in the hierarchy contributes persistent fields.
     *
     * <p>JPA only maps the entity class itself and its {@code @MappedSuperclass}
     * ancestors. Fields declared by a plain Java superclass are not persistent,
     * even though Hibernate-Lite used to include them in its metadata.</p>
     *
     * @param root  the mapped entity class (always included)
     * @param candidate class currently being inspected
     * @return {@code true} if the candidate's fields are persistent
     */
    private static boolean isMappedClass(Class<?> root, Class<?> candidate) {
        return candidate == root
                || candidate.isAnnotationPresent(jakarta.persistence.Entity.class)
                || candidate.isAnnotationPresent(MappedSuperclass.class);
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
            if (!isMappedClass(clazz, c)) continue;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                /*
                 * @Transient fields are not persistent attributes. Including them
                 * made field validation accept names that JPA then rejects much
                 * later with a far less helpful message.
                 */
                if (f.isAnnotationPresent(Transient.class)) continue;
                f.setAccessible(true);
                result.put(f.getName(), f);
            }
        }
        return result;
    }

}
