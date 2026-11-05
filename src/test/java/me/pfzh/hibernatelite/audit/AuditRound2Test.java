package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Second-round, source-derived regression tests.
 *
 * <p>These tests cover defects that were not exercised by the first audit
 * suite:</p>
 * <ul>
 *     <li>{@code saveAll()} must not evict entities the caller loaded in the
 *     same transaction.</li>
 *     <li>Optimistic locking must also protect {@code delete()} and the
 *     exception must be a {@link HibernateLiteException}.</li>
 *     <li>Metadata must ignore Java-transient fields and fields declared by a
 *     non-mapped superclass.</li>
 * </ul>
 */
class AuditRound2Test {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-round2");
        db = AuditSupport.newDataStore(ds,
                TestUser.class,
                VersionedUser.class,
                JavaTransientEntity.class,
                PlainChild.class);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ------------------------------------------------------------------
    // saveAll() and the persistence context
    // ------------------------------------------------------------------

    @Test
    void saveAllInsideTransaction_mustNotDiscardChangesToOtherManagedEntities() {
        Long id = db.transaction(() -> {
            TestUser outer = db.save(new TestUser("outer"));

            List<TestUser> batch = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                batch.add(new TestUser("batch-" + i));
            }
            db.saveAll(batch);          // crosses the 50-row boundary

            outer.setName("outer-updated");
            return outer.getId();
        });

        assertEquals(51, db.query(TestUser.class).count());
        TestUser reloaded = db.find(TestUser.class, id);
        assertNotNull(reloaded);
        assertEquals("outer-updated", reloaded.getName(),
                "saveAll() must not detach entities loaded by the caller");
    }

    @Test
    void saveAll_stillDetachesTheEntitiesOfTheFlushedBatch() {
        Long id = db.transaction(() -> {
            List<TestUser> batch = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                batch.add(new TestUser("batch-" + i));
            }
            db.saveAll(batch);

            // The first entity belongs to the flushed batch and must be
            // detached, so this change must not reach the database.
            batch.get(0).setName("changed-after-detach");
            return batch.get(0).getId();
        });

        TestUser reloaded = db.find(TestUser.class, id);
        assertNotNull(reloaded);
        assertEquals("batch-0", reloaded.getName());
    }

    // ------------------------------------------------------------------
    // Optimistic locking
    // ------------------------------------------------------------------

    @Test
    void staleVersionedSave_isWrappedAsHibernateLiteException() {
        VersionedUser original = db.save(new VersionedUser("alice", 100));
        Long id = original.getId();

        VersionedUser first = db.find(VersionedUser.class, id);
        VersionedUser stale = db.find(VersionedUser.class, id);

        first.setBalance(120);
        db.save(first);

        stale.setBalance(90);
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.save(stale),
                "a stale @Version must not be allowed to overwrite a newer row");
        assertNotNull(ex.getCause(), "the JPA OptimisticLockException should be the cause");
    }

    @Test
    void staleVersionedDelete_isRejected() {
        VersionedUser original = db.save(new VersionedUser("bob", 100));
        Long id = original.getId();

        VersionedUser stale = db.find(VersionedUser.class, id);
        VersionedUser current = db.find(VersionedUser.class, id);

        current.setBalance(150);
        db.save(current);

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.delete(stale),
                "deleting through a stale snapshot must not remove newer data");
        assertTrue(ex.getMessage().contains("Stale entity"), ex.getMessage());
        assertNotNull(db.find(VersionedUser.class, id),
                "the row must still exist after the rejected delete");
    }

    @Test
    void currentVersionedDelete_stillWorks() {
        VersionedUser original = db.save(new VersionedUser("carol", 100));
        Long id = original.getId();

        VersionedUser current = db.find(VersionedUser.class, id);
        db.delete(current);

        assertNull(db.find(VersionedUser.class, id));
        assertEquals(0, db.query(VersionedUser.class).count());
    }

    // ------------------------------------------------------------------
    // Metadata: only real persistent attributes
    // ------------------------------------------------------------------

    @Test
    void javaTransientField_isRejectedBeforeHittingHibernate() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(JavaTransientEntity.class)
                        .eq(JavaTransientEntity::getCache, "x")
                        .list());
        assertTrue(ex.getMessage().contains("has no field"), ex.getMessage());
    }

    @Test
    void fieldFromNonMappedSuperclass_isRejectedBeforeHittingHibernate() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(PlainChild.class)
                        .eq(PlainChild::getBaseValue, "x")
                        .list());
        assertTrue(ex.getMessage().contains("has no field"), ex.getMessage());
    }

    @Test
    void nonMappedSuperclassFieldIsNotInTheJpaMetamodel() {
        var entityType = db.unwrap(org.hibernate.SessionFactory.class)
                .getMetamodel()
                .entity(PlainChild.class);
        assertThrows(IllegalArgumentException.class,
                () -> entityType.getAttribute("baseValue"),
                "JPA must not expose fields of a non-mapped superclass");
    }

    @Test
    void plainSuperclassDoesNotBreakEntityMapping() {
        PlainChild child = new PlainChild();
        child.setId(1L);
        child.setName("n");
        child.setBaseValue("b");
        assertEquals("b", child.getBaseValue());

        assertEquals(0, db.query(PlainChild.class).count());
        assertFalse(db.query(PlainChild.class).eq(PlainChild::getName, "missing").exists());
    }

    // ------------------------------------------------------------------
    // Test entities
    // ------------------------------------------------------------------

    @Entity
    @Table(name = "audit_round2_versioned")
    static class VersionedUser {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Version
        private Long version;

        private String name;
        private Integer balance;

        VersionedUser() {
        }

        VersionedUser(String name, Integer balance) {
            this.name = name;
            this.balance = balance;
        }

        Long getId() { return id; }
        Long getVersion() { return version; }
        String getName() { return name; }
        void setName(String name) { this.name = name; }
        Integer getBalance() { return balance; }
        void setBalance(Integer balance) { this.balance = balance; }
    }

    @Entity
    @Table(name = "audit_round2_transient")
    static class JavaTransientEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        private transient String cache;

        Long getId() { return id; }
        String getName() { return name; }
        String getCache() { return cache; }
        void setCache(String cache) { this.cache = cache; }
    }

    static class PlainBase {
        private String baseValue;

        String getBaseValue() { return baseValue; }
        void setBaseValue(String baseValue) { this.baseValue = baseValue; }
    }

    @Entity
    @Table(name = "audit_round2_plain_child")
    static class PlainChild extends PlainBase {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        Long getId() { return id; }
        void setId(Long id) { this.id = id; }
        String getName() { return name; }
        void setName(String name) { this.name = name; }
    }
}
