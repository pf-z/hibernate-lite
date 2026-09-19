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

    private String status;

    public TestUser() {}

    public TestUser(String name) {
        this.name = name;
    }

    public TestUser(String name, Integer age) {
        this.name = name;
        this.age = age;
    }


    public TestUser(String name, Integer age, String status) {
        this.name = name;
        this.age = age;
        this.status = status;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}