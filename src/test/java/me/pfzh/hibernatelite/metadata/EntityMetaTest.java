package me.pfzh.hibernatelite.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class EntityMetaTest {

    // ==================== 测试实体 ====================

    @Entity
    static class SimpleEntity {
        @Id
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    @Entity
    static class GeneratedIdEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    @Entity
    static class BusinessKeyEntity {
        @Id
        private String username;
        private String email;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    @MappedSuperclass
    static class BaseEntity {
        @Id
        private Long id;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    @Entity
    static class ChildEntity extends BaseEntity {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class NoIdEntity {
        private String name;
    }

    @Entity
    static class MultiIdEntity {
        @Id
        private Long id1;

        @Id
        private Long id2;
    }

    @Entity
    static class StaticFieldEntity {
        @Id
        private Long id;

        private static final String CONSTANT = "x";

        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    // ==================== idField 查找 ====================

    @Test
    void findIdField_simpleEntity() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertNotNull(meta);
        assertEquals(SimpleEntity.class, meta.getEntityClass());
    }

    @Test
    void findIdField_fromSuperclass() {
        EntityMeta meta = new EntityMeta(ChildEntity.class);
        ChildEntity c = new ChildEntity();
        c.setId(42L);
        assertEquals(Long.valueOf(42L), meta.getId(c));
    }

    @Test
    void findIdField_missing_throws() {
        assertThrows(HibernateLiteException.class,
                () -> new EntityMeta(NoIdEntity.class));
    }

    @Test
    void findIdField_multipleIds_throws() {
        assertThrows(HibernateLiteException.class,
                () -> new EntityMeta(MultiIdEntity.class));
    }

    // ==================== getId ====================

    @Test
    void getId_returnsValue() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        SimpleEntity e = new SimpleEntity();
        e.setId(1L);
        assertEquals(Long.valueOf(1L), meta.getId(e));
    }

    @Test
    void getId_nullEntity_throws() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertThrows(IllegalArgumentException.class, () -> meta.getId(null));
    }

    @Test
    void getId_wrongType_throws() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertThrows(IllegalArgumentException.class,
                () -> meta.getId(new Object()));
    }

    // ==================== hasGeneratedId ====================

    @Test
    void hasGeneratedId_true() {
        EntityMeta meta = new EntityMeta(GeneratedIdEntity.class);
        assertTrue(meta.hasGeneratedId());
    }

    @Test
    void hasGeneratedId_false() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertFalse(meta.hasGeneratedId());
    }

    @Test
    void hasGeneratedId_businessKey_false() {
        EntityMeta meta = new EntityMeta(BusinessKeyEntity.class);
        assertFalse(meta.hasGeneratedId());
    }

    // ==================== getField ====================

    @Test
    void getField_existing() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        Field f = meta.getField("name");
        assertNotNull(f);
        assertEquals("name", f.getName());
    }

    @Test
    void getField_missing_returnsNull() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertNull(meta.getField("nonexistent"));
    }

    @Test
    void getField_null_returnsNull() {
        EntityMeta meta = new EntityMeta(SimpleEntity.class);
        assertNull(meta.getField(null));
    }

    @Test
    void getField_inherited() {
        EntityMeta meta = new EntityMeta(ChildEntity.class);
        assertNotNull(meta.getField("name"));
        assertNotNull(meta.getField("id"));   // 父类字段
    }

    // ==================== 字段过滤 ====================

    @Test
    void scanFields_skipsStaticFields() {
        EntityMeta meta = new EntityMeta(StaticFieldEntity.class);
        assertNull(meta.getField("CONSTANT"), "static field should be skipped");
        assertNotNull(meta.getField("name"));
        assertNotNull(meta.getField("id"));
    }
}