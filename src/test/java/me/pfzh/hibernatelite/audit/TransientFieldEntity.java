package me.pfzh.hibernatelite.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Source-derived edge case: a {@code @Transient} Java field.
 *
 * <p>{@code EntityMeta.scanFields} only skips {@code static} and {@code synthetic}
 * fields, so {@code @Transient} fields end up in the metadata field map and pass
 * {@code QueryBuilder.validateField} / the update-field validation.</p>
 */
@Entity
@Table(name = "audit_transient")
public class TransientFieldEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Transient
    private String transientOnly;

    public TransientFieldEntity() {}

    public TransientFieldEntity(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTransientOnly() { return transientOnly; }
    public void setTransientOnly(String v) { this.transientOnly = v; }
}
