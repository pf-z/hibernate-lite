package me.pfzh.hibernatelite.audit;

import jakarta.persistence.*;

/**
 * Audit fixture: child side of a lazy association.
 */
@Entity
@Table(name = "audit_books")
public class AuditBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    private AuditAuthor author;

    public AuditBook() {}

    public AuditBook(String title, AuditAuthor author) {
        this.title = title;
        this.author = author;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public AuditAuthor getAuthor() { return author; }
    public void setAuthor(AuditAuthor author) { this.author = author; }
}
