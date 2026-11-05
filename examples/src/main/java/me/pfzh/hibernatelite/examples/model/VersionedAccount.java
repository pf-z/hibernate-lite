package me.pfzh.hibernatelite.examples.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Versioned entity used by {@code OptimisticLockingExample}.
 *
 * <p>The {@link Version} field makes Hibernate-Lite reject a {@code save()} or
 * {@code delete()} that is based on a stale snapshot.</p>
 */
@Entity
@Table(name = "ex_versioned_accounts")
public class VersionedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    private String owner;

    private Integer balance;

    public VersionedAccount() {
    }

    public VersionedAccount(String owner, Integer balance) {
        this.owner = owner;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }

    @Override
    public String toString() {
        return "VersionedAccount(" + owner + ", " + balance + ", v" + version + ")";
    }
}
