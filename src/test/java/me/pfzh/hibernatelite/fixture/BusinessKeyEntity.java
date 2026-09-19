package me.pfzh.hibernatelite.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * BusinessKeyEntity
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
@Entity
public class BusinessKeyEntity {

    @Id
    private String username;

    protected BusinessKeyEntity() {}

    public BusinessKeyEntity(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
