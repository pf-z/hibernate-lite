package me.pfzh.hibernatelite.audit;

import jakarta.persistence.*;

/**
 * Audit fixture: parent side of a lazy association.
 */
@Entity
@Table(name = "audit_authors")
public class AuditAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public AuditAuthor() {}

    public AuditAuthor(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
