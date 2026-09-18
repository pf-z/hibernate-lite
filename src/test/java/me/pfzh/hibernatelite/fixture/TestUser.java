package me.pfzh.hibernatelite.fixture;

import jakarta.persistence.*;

@Entity
@Table(name = "test_users")
public class TestUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer age;

    public TestUser() {}

    public TestUser(String name) {
        this.name = name;
    }

    public TestUser(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
}