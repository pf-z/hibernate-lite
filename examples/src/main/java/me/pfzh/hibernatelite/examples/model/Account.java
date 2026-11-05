package me.pfzh.hibernatelite.examples.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Business-key entity: the identifier is a business value, there is no
 * {@code @GeneratedValue}.
 *
 * <p>{@code DataStore.save} refuses this kind of entity (see
 * {@code CrudExample}), so it is inserted through the native
 * {@code SessionFactory} escape hatch.</p>
 */
@Entity
@Table(name = "ex_accounts")
public class Account {

    @Id
    private String username;

    private Integer balance;

    public Account() {
    }

    public Account(String username, Integer balance) {
        this.username = username;
        this.balance = balance;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }

    @Override
    public String toString() {
        return "Account(" + username + ", " + balance + ")";
    }
}
