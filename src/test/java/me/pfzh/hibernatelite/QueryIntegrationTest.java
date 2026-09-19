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

class QueryIntegrationTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:query-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");

        db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")
                .build();

        db.saveAll(List.of(
                new TestUser("Alice", 25),
                new TestUser("Bob", 17),
                new TestUser("Charlie", 30),
                new TestUser("Dave", 15),
                new TestUser("Eve", 22)
        ));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.unwrap(SessionFactory.class).close();
        }
        ds.close();
    }

    // ==================== eq / ne ====================

    @Test
    void eq_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .list();
        assertEquals(1, users.size());
        assertEquals("Alice", users.get(0).getName());
    }

    @Test
    void eq_noMatch_returnsEmpty() {
        List<TestUser> users = db.query(TestUser.class)
                .eq(TestUser::getName, "Nonexistent")
                .list();
        assertTrue(users.isEmpty());
    }

    @Test
    void ne_returnsNonMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .ne(TestUser::getName, "Alice")
                .list();
        assertEquals(4, users.size());
        assertTrue(users.stream().noneMatch(u -> "Alice".equals(u.getName())));
    }

    // ==================== 比较 ====================

    @Test
    void gt_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .list();
        assertEquals(3, users.size());
    }

    @Test
    void ge_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .ge(TestUser::getAge, 22)
                .list();
        assertEquals(3, users.size());
    }

    @Test
    void lt_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .lt(TestUser::getAge, 22)
                .list();
        assertEquals(2, users.size());
    }

    @Test
    void le_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .le(TestUser::getAge, 17)
                .list();
        assertEquals(2, users.size());
    }

    // ==================== like / in / between ====================

    @Test
    void like_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .like(TestUser::getName, "A%")
                .list();
        assertEquals(1, users.size());
        assertEquals("Alice", users.get(0).getName());
    }

    @Test
    void in_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .in(TestUser::getName, List.of("Alice", "Bob", "Eve"))
                .list();
        assertEquals(3, users.size());
    }

    @Test
    void notIn_returnsNonMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .notIn(TestUser::getName, List.of("Alice", "Bob"))
                .list();
        assertEquals(3, users.size());
    }

    @Test
    void between_returnsMatching() {
        List<TestUser> users = db.query(TestUser.class)
                .between(TestUser::getAge, 18, 25)
                .list();
        assertEquals(2, users.size());
    }

    // ==================== isNull / isNotNull ====================

    @Test
    void isNull_noMatches() {
        List<TestUser> users = db.query(TestUser.class)
                .isNull(TestUser::getName)
                .list();
        assertTrue(users.isEmpty());
    }

    @Test
    void isNotNull_allMatches() {
        List<TestUser> users = db.query(TestUser.class)
                .isNotNull(TestUser::getName)
                .list();
        assertEquals(5, users.size());
    }

    // ==================== 多条件 AND ====================

    @Test
    void multipleConditions_areAnded() {
        List<TestUser> users = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .lt(TestUser::getAge, 26)
                .list();
        assertEquals(2, users.size());
    }

    // ==================== 排序 ====================

    @Test
    void orderByAsc() {
        List<TestUser> users = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .list();
        assertEquals(15, users.get(0).getAge());
        assertEquals(30, users.get(users.size() - 1).getAge());
    }

    @Test
    void orderByDesc() {
        List<TestUser> users = db.query(TestUser.class)
                .orderByDesc(TestUser::getAge)
                .list();
        assertEquals(30, users.get(0).getAge());
        assertEquals(15, users.get(users.size() - 1).getAge());
    }

    @Test
    void orderByMultiple() {
        List<TestUser> users = db.query(TestUser.class)
                .orderByAsc(TestUser::getName)
                .list();
        assertEquals("Alice", users.get(0).getName());
        assertEquals("Eve", users.get(users.size() - 1).getName());
    }

    // ==================== 聚合 ====================

    @Test
    void count_returnsCorrectValue() {
        long count = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .count();
        assertEquals(3, count);
    }

    @Test
    void count_noConditions_returnsAll() {
        long count = db.query(TestUser.class).count();
        assertEquals(5, count);
    }

    @Test
    void exists_true() {
        assertTrue(db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .exists());
    }

    @Test
    void exists_false() {
        assertFalse(db.query(TestUser.class)
                .eq(TestUser::getName, "Nonexistent")
                .exists());
    }

    // ==================== one ====================

    @Test
    void one_returnsSingle() {
        TestUser u = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .one();
        assertNotNull(u);
        assertEquals("Alice", u.getName());
    }

    @Test
    void one_noMatch_returnsNull() {
        assertNull(db.query(TestUser.class)
                .eq(TestUser::getName, "Nonexistent")
                .one());
    }

    // ==================== list with limit ====================

    @Test
    void listWithLimit_restrictsRows() {
        List<TestUser> users = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .list(2);
        assertEquals(2, users.size());
    }

    // ==================== delete ====================

    @Test
    void delete_removesMatching() {
        int deleted = db.query(TestUser.class)
                .lt(TestUser::getAge, 18)
                .delete();
        assertEquals(2, deleted);
        assertEquals(3, db.query(TestUser.class).count());
    }

    @Test
    void delete_withoutConditions_throws() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).delete());

        assertTrue(ex.getMessage().contains("refusing to delete all rows"));
        assertEquals(5, db.query(TestUser.class).count());
    }

    @Test
    void delete_withWhereSpec_works() {
        int deleted = db.query(TestUser.class)
                .where((cb, root) -> cb.lt(root.get("age"), 18))
                .delete();

        assertEquals(2, deleted);
        assertEquals(3, db.query(TestUser.class).count());
    }

    @Test
    void delete_withConditionsAndSpecs_bothApply() {
        int deleted = db.query(TestUser.class)
                .gt(TestUser::getAge, 10)
                .where((cb, root) -> cb.lt(root.get("age"), 18))
                .delete();

        assertEquals(2, deleted);
        assertEquals(3, db.query(TestUser.class).count());
    }

    // ==================== page ====================

    @Test
    void page_firstPage() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 2));

        assertEquals(2, page.numberOfElements());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void page_lastPage() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(2, 2));

        assertEquals(1, page.numberOfElements());
        assertEquals(5, page.totalElements());
        assertFalse(page.hasNext());
        assertTrue(page.hasPrevious());
        assertTrue(page.isLast());
    }

    @Test
    void page_withoutCount_skipsTotal() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 2).withoutCount());

        assertEquals(2, page.numberOfElements());
        assertFalse(page.hasTotal());
        assertThrows(IllegalStateException.class, page::totalElements);
        assertTrue(page.hasNext());
    }

    @Test
    void page_withConditions() {
        Page<TestUser> page = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 2));

        assertEquals(2, page.numberOfElements());
        assertEquals(3, page.totalElements());
    }

    // ==================== slice hasNext 精确性 ====================

    @Test
    void slice_lastPageExactlyFull_hasNextFalse() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 5).withoutCount());

        assertEquals(5, page.numberOfElements());
        assertFalse(page.hasNext());
        assertTrue(page.isLast());
    }

    @Test
    void slice_morePagesExist_hasNextTrue() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3).withoutCount());

        assertEquals(3, page.numberOfElements());
        assertTrue(page.hasNext());
        assertFalse(page.isLast());
    }

    @Test
    void slice_middlePage() {
        Page<TestUser> page = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(1, 3).withoutCount());

        assertEquals(2, page.numberOfElements());
        assertFalse(page.hasNext());
        assertTrue(page.isLast());
    }

    // ==================== 逃生舱 ====================

    @Test
    void where_rawCriteria() {
        List<TestUser> users = db.query(TestUser.class)
                .where((cb, root) -> cb.like(root.get("name"), "A%"))
                .list();
        assertEquals(1, users.size());
    }

    @Test
    void where_combinedWithDsl() {
        List<TestUser> users = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .where((cb, root) -> cb.like(root.get("name"), "A%"))
                .list();
        assertEquals(1, users.size());
    }
}