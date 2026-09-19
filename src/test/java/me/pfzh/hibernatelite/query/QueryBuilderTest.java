package me.pfzh.hibernatelite.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QueryBuilder}.
 *
 * <p>Only package-visible pure logic ({@code validateField}) is
 * tested here. Full DSL behavior is covered by integration tests
 * against an in-memory database.</p>
 */
class QueryBuilderTest {

    @Entity
    static class User {
        @Id
        private Long id;
        private String name;
        private Integer age;
    }

    private EntityMeta meta;

    @BeforeEach
    void setUp() {
        MetadataRegistry registry = new MetadataRegistry();
        meta = registry.get(User.class);
    }

    // ==================== validateField ====================

    @Test
    void validateField_existingField_ok() {
        assertDoesNotThrow(() -> QueryBuilder.validateField("name", meta));
    }

    @Test
    void validateField_idField_ok() {
        assertDoesNotThrow(() -> QueryBuilder.validateField("id", meta));
    }

    @Test
    void validateField_missingField_throws() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> QueryBuilder.validateField("nonexistent", meta));

        assertTrue(ex.getMessage().contains("no field"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void validateField_messageContainsEntityName() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> QueryBuilder.validateField("nonexistent", meta));

        assertTrue(ex.getMessage().contains(User.class.getName()));
    }
}