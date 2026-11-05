package me.pfzh.hibernatelite.examples.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity with a lazily loaded association.
 *
 * <p>Named {@code Purchase} rather than {@code Order} because {@code ORDER} is a
 * reserved word in JPQL.</p>
 */
@Entity
@Table(name = "ex_purchases")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String product;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    public Purchase() {
    }

    public Purchase(String product, User user) {
        this.product = product;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    @Override
    public String toString() {
        return "Purchase#" + id + "(" + product + ")";
    }
}
