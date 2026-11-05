package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.internal.SessionContext;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 - source-derived invariant for lazy proxies.
 *
 * <p>Source being analysed: {@code CrudExecutor.doSave()} / {@code CrudExecutor.delete()}</p>
 *
 * <pre>
 * EntityMeta meta = metadataRegistry.get(entity.getClass());   // proxy class!
 * Object id = meta.getId(entity);                              // reflective field read
 * if (id == null) { session.persist(entity); ... }
 * </pre>
 *
 * <p>When {@code entity} is a Hibernate proxy, {@code entity.getClass()} is the
 * generated proxy subclass. {@code EntityMeta} is then built for the proxy class
 * (a distinct cache entry), and {@code idField.get(proxy)} reads the entity id
 * field directly on the proxy instance, which is not populated until the proxy is
 * initialised.</p>
 */
class AuditProxyTest {

    private DataStore db;
    private HikariDataSource ds;
    private SessionFactory sf;
    private Long authorId;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-proxy");
        db = AuditSupport.newDataStore(ds, AuditAuthor.class, AuditBook.class);
        AuditAuthor a = db.save(new AuditAuthor("Tolkien"));
        authorId = a.getId();
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    private SessionFactory sf() {
        if (sf == null) sf = db.unwrap(SessionFactory.class);
        return sf;
    }

    /** Obtains a lazily-initialised reference from the library's own Session. */
    private AuditAuthor reference() {
        return SessionContext.current(sf()).getReference(AuditAuthor.class, authorId);
    }

    @Test
    void getReference_returnsUninitializedProxy() {
        db.transaction(() -> {
            AuditAuthor proxy = reference();
            assertFalse(Hibernate.isInitialized(proxy));
            assertNotEquals(AuditAuthor.class, proxy.getClass(),
                    "expected a generated proxy subclass");
            return null;
        });
    }

    @Test
    void entityMeta_onProxyClass_canReadTheIdentifier() {
        db.transaction(() -> {
            AuditAuthor proxy = reference();
            EntityMeta meta = new MetadataRegistry().get(proxy.getClass());
            assertEquals(authorId, meta.getId(proxy),
                    "EntityMeta.getId() returned null for an uninitialised proxy");
            return null;
        });
    }

    @Test
    void save_uninitializedProxy_doesNotThrowAndDoesNotDuplicate() {
        db.transaction(() -> {
            AuditAuthor proxy = reference();
            assertFalse(Hibernate.isInitialized(proxy));
            assertDoesNotThrow(() -> db.save(proxy),
                    "save() on a lazy proxy must not fail");
            return null;
        });
        assertEquals(1, db.query(AuditAuthor.class).count());
    }

    @Test
    void delete_uninitializedProxy_removesTheRow() {
        db.transaction(() -> {
            AuditAuthor proxy = reference();
            assertFalse(Hibernate.isInitialized(proxy));
            assertDoesNotThrow(() -> db.delete(proxy));
            return null;
        });
        assertEquals(0, db.query(AuditAuthor.class).count());
    }

    @Test
    void metadataRegistry_normalizesProxyClasses() {
        db.transaction(() -> {
            AuditAuthor proxy = reference();
            MetadataRegistry r = new MetadataRegistry();
            EntityMeta proxyMeta = r.get(proxy.getClass());
            EntityMeta realMeta = r.get(AuditAuthor.class);
            assertSame(proxyMeta, realMeta,
                    "a proxy class must not get its own metadata entry");
            assertEquals(AuditAuthor.class, proxyMeta.getEntityClass());
            return null;
        });
    }

    /**
     * A proxy obtained inside a transaction becomes detached when the session closes.
     * {@code delete()} then takes the "detached entity" path, which depends entirely on
     * reading the id reflectively - and that read yields {@code null} for a proxy.
     */
    @Test
    void delete_detachedUninitializedProxy_mustWork() {
        AuditAuthor detached = db.transaction(() -> {
            AuditAuthor proxy = reference();
            assertFalse(Hibernate.isInitialized(proxy));
            return proxy;
        });

        assertDoesNotThrow(() -> db.delete(detached),
                "delete() of a detached proxy failed because EntityMeta.getId() returned null");
        assertEquals(0, db.query(AuditAuthor.class).count(), "the author row must be removed");
    }

    @Test
    void save_detachedUninitializedProxy_mustWork() {
        AuditAuthor detached = db.transaction(() -> {
            AuditAuthor proxy = reference();
            assertFalse(Hibernate.isInitialized(proxy));
            return proxy;
        });

        assertDoesNotThrow(() -> db.save(detached),
                "save() of a detached proxy failed because EntityMeta.getId() returned null");
        assertEquals(1, db.query(AuditAuthor.class).count());
    }
}
