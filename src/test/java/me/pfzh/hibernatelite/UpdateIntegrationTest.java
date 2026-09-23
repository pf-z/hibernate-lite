package me.pfzh.hibernatelite;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdateIntegrationTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:update-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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

    // ==================== 单字段更新 ====================

    @Test
    void updateSingleField_bySingleCondition() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .update(TestUser::getAge, 26);

        assertEquals(1, updated);

        TestUser alice = db.query(TestUser.class).eq(TestUser::getName, "Alice").one();
        assertEquals(26, alice.getAge());
    }

    @Test
    void updateSingleField_byMultipleConditions() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .lt(TestUser::getAge, 18)
                .update(TestUser::getStatus, "MINOR");

        assertEquals(2, updated);   // Bob(17), Dave(15)

        long minors = db.query(TestUser.class)
                .eq(TestUser::getStatus, "MINOR")
                .count();
        assertEquals(2, minors);
    }

    @Test
    void updateAllMatching() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .update(TestUser::getStatus, "VERIFIED");

        assertEquals(3, updated);   // Alice, Bob, Dave

        long verified = db.query(TestUser.class)
                .eq(TestUser::getStatus, "VERIFIED")
                .count();
        assertEquals(3, verified);
    }

    @Test
    void update_noMatch_returnsZero() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getName, "Nonexistent")
                .update(TestUser::getAge, 99);

        assertEquals(0, updated);
    }

    // ==================== 多字段更新 ====================

    @Test
    void updateMultipleFields() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .update(Map.of(
                        "age", 26,
                        "status", "VIP"
                ));

        assertEquals(1, updated);

        TestUser alice = db.query(TestUser.class).eq(TestUser::getName, "Alice").one();
        assertEquals(26, alice.getAge());
        assertEquals("VIP", alice.getStatus());
    }

    // ==================== 逃生舱 ====================

    @Test
    void update_withWhereSpec() {
        int updated = db.query(TestUser.class)
                .where((cb, root) -> cb.lt(root.get("age"), 18))
                .update(TestUser::getStatus, "MINOR");

        assertEquals(2, updated);   // Bob(17), Dave(15)
    }

    @Test
    void update_dslAndSpecCombined() {
        int updated = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .where((cb, root) -> cb.lt(root.get("age"), 18))
                .update(TestUser::getStatus, "MINOR_ACTIVE");

        assertEquals(2, updated);   // Bob(17), Dave(15)
    }

    // ==================== 安全保护 ====================

    @Test
    void update_withoutConditions_throws() {
        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).update(TestUser::getStatus, "ALL"));

        assertTrue(ex.getMessage().contains("refusing to update all rows"));

        // 数据没有被改动
        assertEquals(5, db.query(TestUser.class).count());
    }

    @Test
    void update_emptyUpdatesMap_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class)
                        .eq(TestUser::getName, "Alice")
                        .update(Map.of()));
    }

    // ==================== 字段校验 ====================

    @Test
    void update_invalidField_throws() {
        assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class)
                        .eq(TestUser::getName, "Alice")
                        .update(Map.of("nonexistentField", "x")));
    }

    // ==================== 事务回滚 ====================

    @Test
    void update_rollbackOnException() {
        Long aliceId = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .one()
                .getId();

        assertThrows(RuntimeException.class, () ->
                db.transaction(() -> {
                    db.query(TestUser.class)
                            .eq(TestUser::getName, "Alice")
                            .update(TestUser::getAge, 99);
                    throw new RuntimeException("simulated failure");
                }));

        TestUser alice = db.find(TestUser.class, aliceId);
        assertEquals(25, alice.getAge());   // 回滚，age 未改
    }
}