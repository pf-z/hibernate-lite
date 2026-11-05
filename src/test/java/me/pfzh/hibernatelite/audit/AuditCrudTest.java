package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.BusinessKeyEntity;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of {@code DataStore} CRUD semantics.
 *
 * <p>Expectations are taken from {@code README.md} section "2. CRUD" and
 * from the Javadoc of {@link DataStore}.</p>
 */
class AuditCrudTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-crud");
        db = AuditSupport.newDataStore(ds, TestUser.class, BusinessKeyEntity.class);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- find ----------

    @Test
    void find_returnsEntity() {
        TestUser u = db.save(new TestUser("Alice", 30));
        TestUser found = db.find(TestUser.class, u.getId());
        assertNotNull(found);
        assertEquals("Alice", found.getName());
        assertEquals(30, found.getAge());
        assertNotSame(u, found, "find must read from the database, not reuse the passed instance");
    }

    @Test
    void find_missing_returnsNull() {
        assertNull(db.find(TestUser.class, 123456789L));
    }

    @Test
    void find_nullTypeRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> db.find(null, 1L));
        assertTrue(e.getMessage().toLowerCase().contains("type"));
    }

    @Test
    void find_nullIdRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> db.find(TestUser.class, null));
        assertTrue(e.getMessage().toLowerCase().contains("id")
                        || e.getMessage().toLowerCase().contains("primary key"),
                "unexpected message: " + e.getMessage());
    }

    @Test
    void find_wrongIdType_shouldFailClearly() {
        TestUser u = db.save(new TestUser("Alice"));
        // Passing a String where the id is Long: must not silently return garbage.
        assertThrows(RuntimeException.class, () -> db.find(TestUser.class, "not-a-long"));
        assertNotNull(u.getId());
    }

    @Test
    void find_nullEntityType_doesNotCorruptContext() {
        assertThrows(IllegalArgumentException.class, () -> db.find(null, 1L));
        // The ThreadLocal context must be clean afterwards.
        assertTrue(db.query(TestUser.class).count() == 0);
    }

    // ---------- save ----------

    @Test
    void save_insertsAndAssignsGeneratedId() {
        TestUser u = db.save(new TestUser("Alice"));
        assertNotNull(u.getId());
        assertEquals(1, db.query(TestUser.class).count());
    }

    @Test
    void save_nullRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> db.save(null));
        assertTrue(e.getMessage().toLowerCase().contains("entity"));
    }

    @Test
    void save_detachedEntityWithGeneratedId_updates() {
        TestUser inserted = db.save(new TestUser("Alice", 20));
        assertNotNull(inserted.getId());

        TestUser detached = new TestUser("Alice-renamed", 21);
        detached.setId(inserted.getId());
        TestUser merged = db.save(detached);

        assertEquals(inserted.getId(), merged.getId());
        TestUser reloaded = db.find(TestUser.class, inserted.getId());
        assertEquals("Alice-renamed", reloaded.getName());
        assertEquals(21, reloaded.getAge());
        assertEquals(1, db.query(TestUser.class).count(), "must UPDATE, not INSERT");
    }

    @Test
    void save_entityReturnedIsAlwaysUsable() {
        TestUser returned = db.save(new TestUser("Bob", 5));
        assertNotNull(returned);
        assertNotNull(returned.getId());
        assertEquals("Bob", returned.getName());
    }

    @Test
    void save_twiceOnSameDetachedInstance_isNotDuplicated() {
        TestUser u = db.save(new TestUser("Carol"));
        Long id = u.getId();
        TestUser again = db.save(u);
        assertEquals(id, again.getId());
        assertEquals(1, db.query(TestUser.class).count());
    }

    @Test
    void save_businessKeyEntity_isImpossibleThroughPublicApi() {
        // A business key IS the @Id, so it is never null on a well-formed
        // instance. doSave() therefore always takes the "cannot determine
        // semantics" branch and throws.
        // Consequence: the public API offers NO way to INSERT such an entity,
        // even though the Javadoc table claims "ID is null -> INSERT".
        BusinessKeyEntity e = new BusinessKeyEntity("alice");
        HibernateLiteException ex = assertThrows(HibernateLiteException.class, () -> db.save(e));
        assertTrue(ex.getMessage().contains("business identifier"));
        assertEquals(0, db.query(BusinessKeyEntity.class).count());
    }

    @Test
    void save_businessKeyWithNonNullId_throwsAsDocumented() {
        BusinessKeyEntity e = new BusinessKeyEntity("bob");
        HibernateLiteException ex = assertThrows(HibernateLiteException.class, () -> db.save(e));
        assertTrue(ex.getMessage().contains("business identifier"));
        assertEquals(0, db.query(BusinessKeyEntity.class).count());
    }

    // ---------- saveAll ----------

    @Test
    void saveAll_emptyList_returnsEmpty() {
        List<TestUser> out = db.saveAll(List.of());
        assertTrue(out.isEmpty());
        assertEquals(0, db.query(TestUser.class).count());
    }

    @Test
    void saveAll_returnsInputOrderAndIds() {
        List<TestUser> input = new ArrayList<>();
        for (int i = 0; i < 10; i++) input.add(new TestUser("U" + i, i));

        List<TestUser> out = db.saveAll(input);
        assertEquals(10, out.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("U" + i, out.get(i).getName());
            assertNotNull(out.get(i).getId(), "id missing for element " + i);
        }
        assertEquals(10, db.query(TestUser.class).count());
    }

    @Test
    void saveAll_crossesBatchBoundaryOf50() {
        List<TestUser> input = new ArrayList<>();
        for (int i = 0; i < 120; i++) input.add(new TestUser("U" + i, i));

        List<TestUser> out = db.saveAll(input);

        assertEquals(120, out.size());
        assertEquals(120, db.query(TestUser.class).count());
        for (int i = 0; i < 120; i++) {
            assertEquals("U" + i, out.get(i).getName(), "order broken at index " + i);
            assertNotNull(out.get(i).getId(), "id null after batch clear at index " + i);
        }
        // Every id must be distinct.
        assertEquals(120, out.stream().map(TestUser::getId).distinct().count());
    }

    @Test
    void saveAll_exactly50_isFine() {
        List<TestUser> input = new ArrayList<>();
        for (int i = 0; i < 50; i++) input.add(new TestUser("E" + i));
        List<TestUser> out = db.saveAll(input);
        assertEquals(50, out.size());
        assertEquals(50, db.query(TestUser.class).count());
    }

    @Test
    void saveAll_51_isFine() {
        List<TestUser> input = new ArrayList<>();
        for (int i = 0; i < 51; i++) input.add(new TestUser("F" + i));
        assertEquals(51, db.saveAll(input).size());
        assertEquals(51, db.query(TestUser.class).count());
    }

    @Test
    void saveAll_withNullElement_rollsBackEverything() {
        List<TestUser> input = new ArrayList<>();
        input.add(new TestUser("ok1"));
        input.add(null);
        input.add(new TestUser("ok2"));

        assertThrows(HibernateLiteException.class, () -> db.saveAll(input));
        assertEquals(0, db.query(TestUser.class).count(),
                "a failure inside saveAll must roll back the whole batch");
    }

    @Test
    void saveAll_nullListRejected() {
        assertThrows(IllegalArgumentException.class, () -> db.saveAll(null));
    }

    @Test
    void saveAll_emptyList_allowsSubsequentOperations() {
        db.saveAll(List.of());
        assertEquals(0, db.query(TestUser.class).count());
        db.save(new TestUser("after-empty"));
        assertEquals(1, db.query(TestUser.class).count());
    }

    @Test
    void saveAll_mixedWithBusinessKey_rollsBackAll() {
        List<Object> raw = new ArrayList<>();
        raw.add(new TestUser("u1"));
        raw.add(new BusinessKeyEntity("bk"));   // id is non-null -> throws

        assertThrows(HibernateLiteException.class, () -> db.saveAll(raw));
        assertEquals(0, db.query(TestUser.class).count());
        assertEquals(0, db.query(BusinessKeyEntity.class).count());
    }

    // ---------- delete ----------

    @Test
    void delete_managedEntity() {
        TestUser u = db.save(new TestUser("Alice"));
        Long id = u.getId();
        db.delete(u);
        assertNull(db.find(TestUser.class, id));
        assertEquals(0, db.query(TestUser.class).count());
    }

    @Test
    void delete_detachedEntityById() {
        TestUser u = db.save(new TestUser("Alice"));
        Long id = u.getId();

        TestUser detached = new TestUser();
        detached.setId(id);
        db.delete(detached);

        assertNull(db.find(TestUser.class, id));
    }

    @Test
    void delete_isIdempotent() {
        TestUser u = db.save(new TestUser("Alice"));
        Long id = u.getId();
        db.delete(u);
        assertDoesNotThrow(() -> db.delete(u));

        TestUser detached = new TestUser();
        detached.setId(id);
        assertDoesNotThrow(() -> db.delete(detached));
    }

    @Test
    void delete_nonExistentId_isSilent() {
        TestUser detached = new TestUser();
        detached.setId(987654321L);
        assertDoesNotThrow(() -> db.delete(detached));
    }

    @Test
    void delete_entityWithNullId_throws() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.delete(new TestUser("no-id")));
        assertTrue(ex.getMessage().contains("ID is null"));
    }

    @Test
    void delete_nullRejected() {
        assertThrows(IllegalArgumentException.class, () -> db.delete(null));
    }

    @Test
    void delete_doesNotAffectOtherRows() {
        TestUser a = db.save(new TestUser("A"));
        db.save(new TestUser("B"));
        db.save(new TestUser("C"));
        db.delete(a);
        assertEquals(2, db.query(TestUser.class).count());
    }

    @Test
    void delete_businessKeyEntity_detached() {
        // Insert through the escape hatch, because save() cannot create it.
        var sf = db.unwrap(org.hibernate.SessionFactory.class);
        try (var s = sf.openSession()) {
            s.beginTransaction();
            s.persist(new BusinessKeyEntity("alice"));
            s.getTransaction().commit();
        }
        assertEquals(1, db.query(BusinessKeyEntity.class).count());

        BusinessKeyEntity detached = new BusinessKeyEntity("alice");
        db.delete(detached);
        assertEquals(0, db.query(BusinessKeyEntity.class).count());
    }

    // ---------- lifecycle ----------

    @Test
    void close_isIdempotent() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-crud-close");
        DataStore local = AuditSupport.newDataStore(ds2, TestUser.class);
        local.close();
        assertDoesNotThrow(local::close);
        ds2.close();
    }

    @Test
    void useAfterClose_fails() {
        HikariDataSource ds2 = AuditSupport.newDataSource("audit-crud-closed");
        DataStore local = AuditSupport.newDataStore(ds2, TestUser.class);
        local.close();
        assertThrows(RuntimeException.class, () -> local.save(new TestUser("x")));
    }

    @Test
    void tryWithResources_works() {
        assertDoesNotThrow(() -> {
            try (DataStore local = AuditSupport.newDataStore(AuditSupport.newDataSource("audit-crud-twr"), TestUser.class)) {
                local.save(new TestUser("in-try"));
            }
        });
    }

    @Test
    void unwrap_sessionFactory() {
        assertNotNull(db.unwrap(org.hibernate.SessionFactory.class));
    }

    @Test
    void unwrap_nullRejected() {
        assertThrows(IllegalArgumentException.class, () -> db.unwrap(null));
    }

    @Test
    void unwrap_unsupportedType() {
        assertThrows(HibernateLiteException.class, () -> db.unwrap(String.class));
    }

    @Test
    void managerCtorRejectsNulls() {
        assertThrows(IllegalStateException.class, () -> HibernateLite.builder().build());
    }
}
