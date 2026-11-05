package me.pfzh.hibernatelite.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fixture used to force a <b>commit-time</b> failure (NOT NULL violation),
 * so the commit-failure path in {@code SessionContext.commit()} can be exercised.
 */
@Entity
@Table(name = "audit_not_null")
public class NotNullEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String required;

    public NotNullEntity() {}

    public NotNullEntity(String required) {
        this.required = required;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequired() { return required; }
    public void setRequired(String required) { this.required = required; }
}
