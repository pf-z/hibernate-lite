package me.pfzh.hibernatelite.audit;

import jakarta.persistence.*;

/**
 * Source-derived edge case: a {@code @GeneratedValue} identifier declared with a
 * <b>primitive</b> type.
 *
 * <p>{@code CrudExecutor.doSave} decides "new vs. detached" with
 * {@code id == null}. A primitive {@code long} is never {@code null} - it is
 * {@code 0} - so the entity always takes the {@code merge()} branch.</p>
 */
@Entity
@Table(name = "audit_primitive_ids")
public class PrimitiveIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    public PrimitiveIdEntity() {}

    public PrimitiveIdEntity(String name) {
        this.name = name;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
