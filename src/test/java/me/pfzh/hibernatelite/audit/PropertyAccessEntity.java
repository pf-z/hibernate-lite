package me.pfzh.hibernatelite.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Source-derived edge case: Hibernate-valid entity that uses <b>property access</b>
 * ({@code @Id} on the getter) instead of field access.
 *
 * <p>{@code EntityMeta.findIdField} only scans {@code Field}s and never looks at
 * getters, so this mapping is rejected even though Hibernate supports it.</p>
 */
@Entity
@Table(name = "audit_property_access")
public class PropertyAccessEntity {

    private Long id;
    private String name;

    public PropertyAccessEntity() {}

    public PropertyAccessEntity(String name) {
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
