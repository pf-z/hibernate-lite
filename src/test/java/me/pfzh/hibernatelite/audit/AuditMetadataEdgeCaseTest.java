package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.BusinessKeyEntity;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariants of the metadata layer.
 *
 * <p>Source being analysed: {@code metadata/EntityMeta.java} and
 * {@code internal/CrudExecutor.doSave()}.</p>
 */
class AuditMetadataEdgeCaseTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-meta");
        db = AuditSupport.newDataStore(ds,
                PrimitiveIdEntity.class,
                PropertyAccessEntity.class,
                TransientFieldEntity.class,
                BusinessKeyEntity.class);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- primitive identifier ----------

    /**
     * {@code doSave()} branches on {@code id == null}. A primitive {@code long}
     * identifier is {@code 0}, never {@code null}, so a brand-new entity is treated
     * as detached and goes through {@code merge()} instead of {@code persist()}.
     */
    @Test
    void primitiveId_newEntity_isStillInserted() {
        PrimitiveIdEntity e = db.save(new PrimitiveIdEntity("first"));

        assertTrue(e.getId() > 0, "generated primitive id must have been assigned");
        assertEquals(1, db.query(PrimitiveIdEntity.class).count());
    }

    @Test
    void primitiveId_saveAllWorks() {
        List<PrimitiveIdEntity> saved = db.saveAll(List.of(
                new PrimitiveIdEntity("a"),
                new PrimitiveIdEntity("b")));

        assertEquals(2, saved.size());
        saved.forEach(e -> assertTrue(e.getId() > 0));
        assertEquals(2, db.query(PrimitiveIdEntity.class).count());
    }

    @Test
    void primitiveId_secondSaveUpdatesInsteadOfDuplicating() {
        PrimitiveIdEntity e = db.save(new PrimitiveIdEntity("first"));
        e.setName("renamed");
        PrimitiveIdEntity again = db.save(e);

        assertEquals(e.getId(), again.getId());
        assertEquals(1, db.query(PrimitiveIdEntity.class).count());
        assertEquals("renamed", db.find(PrimitiveIdEntity.class, e.getId()).getName());
    }

    // ---------- property access mapping ----------

    @Test
    void propertyAccessEntity_isRejectedWithAClearMessage() {
        // Hibernate itself supports @Id on a getter; EntityMeta only scans fields.
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.save(new PropertyAccessEntity("x")));
        assertTrue(ex.getMessage().contains("@Id"),
                "error should point at the @Id requirement: " + ex.getMessage());
    }

    @Test
    void propertyAccessEntity_metadataLookupFails() {
        assertThrows(HibernateLiteException.class,
                () -> new MetadataRegistry().get(PropertyAccessEntity.class));
    }

    // ---------- @Transient fields ----------

    /**
     * {@code scanFields()} only skips static/synthetic fields, so {@code @Transient}
     * fields are exposed as if they were persistent attributes. Since the metadata
     * map is used exclusively to validate JPA attribute names, a non-persistent
     * field must not be part of it.
     */
    @Test
    void transientField_mustNotBeExposedAsPersistentAttribute() {
        EntityMeta meta = new MetadataRegistry().get(TransientFieldEntity.class);
        assertNull(meta.getField("transientOnly"),
                "@Transient field leaked into the persistent field metadata");
        assertNotNull(meta.getField("name"));
    }

    @Test
    void transientField_inUpdate_isRejectedByTheFramework() {
        db.save(new TransientFieldEntity("a"));

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(TransientFieldEntity.class)
                        .eq(TransientFieldEntity::getName, "a")
                        .update(Map.of("transientOnly", "boom")));
        assertTrue(ex.getMessage().contains("has no field"),
                "expected the framework's field validation to reject it, got: " + ex.getMessage());
    }

    @Test
    void transientField_inQuery_isRejectedByTheFramework() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(TransientFieldEntity.class)
                        .eq(TransientFieldEntity::getTransientOnly, "x")
                        .list());
        assertTrue(ex.getMessage().contains("has no field"),
                "expected the framework's field validation to reject it, got: " + ex.getMessage());
    }

    // ---------- business key ----------

    @Test
    void businessKey_hasNoGeneratedId() {
        EntityMeta meta = new MetadataRegistry().get(BusinessKeyEntity.class);
        assertFalse(meta.hasGeneratedId());
        assertEquals("username", meta.getField("username").getName());
    }

    @Test
    void businessKey_metadataIdReadsTheBusinessValue() {
        EntityMeta meta = new MetadataRegistry().get(BusinessKeyEntity.class);
        assertEquals("alice", meta.getId(new BusinessKeyEntity("alice")));
    }

    // ---------- metadata caching ----------

    @Test
    void metadataIsCachedPerClass() {
        MetadataRegistry r = new MetadataRegistry();
        assertSame(r.get(PrimitiveIdEntity.class), r.get(PrimitiveIdEntity.class));
    }

    @Test
    void metadataRegistryRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new MetadataRegistry().get(null));
    }
}
