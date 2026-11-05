package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.BusinessKeyEntity;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit of the "transparent session management" promise.
 *
 * <p>{@code find()} / {@code list()} close the Session as soon as the call
 * returns, so any lazily loaded association becomes unreadable. This is a
 * direct consequence of the design and is not mentioned anywhere in the
 * README.</p>
 */
class AuditDetachedStateTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-detached");
        db = AuditSupport.newDataStore(ds, AuditAuthor.class, AuditBook.class);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    private AuditBook bookWithLazyAuthor(String title) {
        AuditAuthor author = db.save(new AuditAuthor("Tolkien"));
        // Reference the author only by id so that Hibernate must use a proxy
        // when the book is loaded again.
        AuditAuthor reference = new AuditAuthor();
        reference.setId(author.getId());
        return db.save(new AuditBook(title, reference));
    }

    @Test
    void find_returnsEntityWithEagerState() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");
        AuditBook found = db.find(AuditBook.class, book.getId());
        assertNotNull(found);
        assertEquals("The Hobbit", found.getTitle());
    }

    @Test
    void find_thenAccessLazyAssociation_outsideTransaction() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");
        AuditBook found = db.find(AuditBook.class, book.getId());
        assertNotNull(found);

        // The entity is detached as soon as find() returns, so navigating a
        // lazy association from ordinary business code is impossible.
        assertThrows(org.hibernate.LazyInitializationException.class,
                () -> found.getAuthor().getName(),
                "expected detached lazy association to fail");
    }

    @Test
    void transaction_thenAccessLazyAssociation_worksInsideCallback() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");

        String name = db.transaction(() -> {
            AuditBook inside = db.find(AuditBook.class, book.getId());
            return inside.getAuthor().getName();
        });
        assertEquals("Tolkien", name);
    }

    @Test
    void queryList_returnsDetachedEntitiesToo() {
        bookWithLazyAuthor("The Hobbit");

        List<AuditBook> books = db.query(AuditBook.class).list();
        assertEquals(1, books.size());
        assertThrows(org.hibernate.LazyInitializationException.class,
                () -> books.get(0).getAuthor().getName());
    }

    @Test
    void save_returnsDetachedEntity() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");
        AuditBook saved = db.save(book);
        assertNotNull(saved.getId());
        assertThrows(org.hibernate.LazyInitializationException.class,
                () -> saved.getAuthor().getName());
    }

    /**
     * NOTE: an earlier variant observed detachment through a lazy association,
     * but the association is the caller-supplied instance, not a proxy, so
     * detachment after the 50-row boundary cannot be asserted through the
     * public API. This test only verifies the batch itself stays correct.
     */
    @Test
    void saveAll_crossingBatchBoundaryKeepsAllRows() {
        AuditAuthor author = db.save(new AuditAuthor("A"));
        AuditAuthor reference = new AuditAuthor();
        reference.setId(author.getId());

        List<AuditBook> books = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) books.add(new AuditBook("b" + i, reference));

        List<AuditBook> saved = db.saveAll(books);
        assertEquals(60, saved.size());
        assertEquals(60, db.query(AuditBook.class).count());
        assertEquals(60, saved.stream().map(AuditBook::getId).distinct().count());
    }

    @Test
    void mergeOfDetachedGraph_doesNotResolveLazyProxy() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");
        AuditBook detached = db.find(AuditBook.class, book.getId());

        // Re-saving the detached instance must not blow up on the proxy.
        assertDoesNotThrow(() -> db.save(detached));
        assertEquals(1, db.query(AuditBook.class).count());
    }

    @Test
    void delete_detachedWithLazyProxy_works() {
        AuditBook book = bookWithLazyAuthor("The Hobbit");
        AuditBook detached = db.find(AuditBook.class, book.getId());
        assertDoesNotThrow(() -> db.delete(detached));
        assertEquals(0, db.query(AuditBook.class).count());
    }

    @Test
    void businessKeyEntityCannotBeCreatedBySave() {
        // Cross-check of the CRUD limitation using a second entity type.
        BusinessKeyEntity bk = new BusinessKeyEntity("x");
        assertThrows(RuntimeException.class, () -> db.save(bk));
        assertThrows(RuntimeException.class, () -> db.saveAll(List.of(bk)));
    }
}
