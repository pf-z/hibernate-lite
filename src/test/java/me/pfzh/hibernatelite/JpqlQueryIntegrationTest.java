package me.pfzh.hibernatelite;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JpqlQueryIntegrationTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:jpql-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");

        db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")
                .build();

        db.saveAll(List.of(
                new TestUser("Alice", 25, "ACTIVE"),
                new TestUser("Bob", 17, "ACTIVE"),
                new TestUser("Charlie", 30, "INACTIVE"),
                new TestUser("Dave", 15, "ACTIVE"),
                new TestUser("Eve", 22, "INACTIVE")
        ));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.unwrap(SessionFactory.class).close();
        }
        ds.close();
    }

    // ==================== 基础查询 ====================

    @Test
    void list_all() {
        List<TestUser> users = db.query("SELECT u FROM TestUser u", TestUser.class)
                .list();
        assertEquals(5, users.size());
    }

    @Test
    void list_withSingleParam() {
        List<TestUser> users = db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Alice")
                .list();
        assertEquals(1, users.size());
        assertEquals("Alice", users.get(0).getName());
    }

    @Test
    void list_withMultipleParams() {
        List<TestUser> users = db.query(
                        "SELECT u FROM TestUser u WHERE u.age > :minAge AND u.status = :status",
                        TestUser.class)
                .param("minAge", 18)
                .param("status", "ACTIVE")
                .list();
        assertEquals(1, users.size());
        assertEquals("Alice", users.get(0).getName());
    }

    @Test
    void list_withLimit() {
        List<TestUser> users = db.query("SELECT u FROM TestUser u ORDER BY u.age", TestUser.class)
                .list(2);
        assertEquals(2, users.size());
        assertEquals(15, users.get(0).getAge());
    }

    @Test
    void list_orderedByAge() {
        List<TestUser> users = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age DESC",
                        TestUser.class)
                .list();
        assertEquals(30, users.get(0).getAge());
        assertEquals(15, users.get(users.size() - 1).getAge());
    }

    // ==================== one ====================

    @Test
    void one_singleMatch() {
        TestUser u = db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Alice")
                .one();
        assertNotNull(u);
        assertEquals("Alice", u.getName());
    }

    @Test
    void one_noMatch_returnsNull() {
        TestUser u = db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Nonexistent")
                .one();
        assertNull(u);
    }

    @Test
    void one_multipleMatches_throws() {
        assertThrows(HibernateLiteException.class, () ->
                db.query("SELECT u FROM TestUser u WHERE u.status = :status", TestUser.class)
                        .param("status", "ACTIVE")
                        .one());
    }

    // ==================== count ====================

    @Test
    void count_all() {
        long count = db.query("SELECT COUNT(u) FROM TestUser u", Long.class)
                .count();
        assertEquals(5, count);
    }

    @Test
    void count_withParam() {
        long count = db.query(
                        "SELECT COUNT(u) FROM TestUser u WHERE u.status = :status",
                        Long.class)
                .param("status", "ACTIVE")
                .count();
        assertEquals(3, count);
    }

    // ==================== exists ====================

    @Test
    void exists_true() {
        assertTrue(db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Alice")
                .exists());
    }

    @Test
    void exists_false() {
        assertFalse(db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Nonexistent")
                .exists());
    }

    // ==================== page ====================

    @Test
    void page_withCount() {
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(0, 2));

        assertEquals(2, page.numberOfElements());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
    }

    @Test
    void page_withCountAndFilter() {
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u WHERE u.status = :status ORDER BY u.age",
                        TestUser.class)
                .param("status", "ACTIVE")
                .page(
                        "SELECT COUNT(u) FROM TestUser u WHERE u.status = :status",
                        PageRequest.of(0, 2));

        assertEquals(2, page.numberOfElements());
        assertEquals(3, page.totalElements());
    }

    @Test
    void page_sliceMode() {
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(0, 3).withoutCount());

        assertEquals(3, page.numberOfElements());
        assertFalse(page.hasTotal());
        assertTrue(page.hasNext());
    }

    @Test
    void page_sliceLastPage() {
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(1, 3).withoutCount());

        assertEquals(2, page.numberOfElements());
        assertFalse(page.hasNext());
        assertTrue(page.isLast());
    }

    // ==================== 参数校验 ====================

    @Test
    void nullJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(null, TestUser.class));
    }

    @Test
    void blankJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("   ", TestUser.class));
    }

    @Test
    void nullResultType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query("SELECT u FROM TestUser u", null));
    }

    @Test
    void invalidJpql_throws() {
        // Hibernate 6 在解析 JPQL 时可能抛 IllegalArgumentException
        assertThrows(RuntimeException.class, () ->
                db.query("SELECT x FROM NonExistentEntity x", TestUser.class)
                        .list());
    }
}