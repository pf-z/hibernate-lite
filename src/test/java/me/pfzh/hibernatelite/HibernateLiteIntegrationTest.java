package me.pfzh.hibernatelite;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.internal.SessionContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HibernateLiteIntegrationTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");

        db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")        // ← 加这行
                .build();
    }

    @AfterEach
    void tearDown() {
        // ★ 删掉这行：SessionContext.rollback();

        if (db != null) {
            db.unwrap(SessionFactory.class).close();
        }
        ds.close();
    }

    @Test
    void save_and_find() {
        TestUser u = db.save(new TestUser("Alice"));
        assertNotNull(u.getId());

        TestUser found = db.find(TestUser.class, u.getId());
        assertEquals("Alice", found.getName());
    }

    @Test
    void find_nonExistent_returnsNull() {
        assertNull(db.find(TestUser.class, 999999L));
    }

    @Test
    void saveAll_batch() {
        List<TestUser> result = db.saveAll(List.of(
                new TestUser("A"),
                new TestUser("B"),
                new TestUser("C")
        ));

        assertEquals(3, result.size());
        result.forEach(u -> assertNotNull(u.getId()));
    }

    @Test
    void delete_removes() {
        TestUser u = db.save(new TestUser("Alice"));
        db.delete(u);

        assertNull(db.find(TestUser.class, u.getId()));
    }

    @Test
    void transaction_commits() {
        Long id = db.transaction(() -> {
            TestUser u = db.save(new TestUser("Bob"));
            return u.getId();
        });

        assertNotNull(db.find(TestUser.class, id));
    }

    @Test
    void transaction_rollsBackOnException() {
        Long id = db.save(new TestUser("Charlie")).getId();
        assertNotNull(db.find(TestUser.class, id));

        assertThrows(RuntimeException.class, () ->
                db.transaction(() -> {
                    TestUser u = db.find(TestUser.class, id);
                    db.delete(u);
                    throw new RuntimeException("rollback!");
                })
        );

        assertNotNull(db.find(TestUser.class, id));
    }

    @Test
    void nestedTransaction_usesOuterTransaction() {
        String result = db.transaction(() -> {
            db.save(new TestUser("Inner1"));
            db.transaction(() -> {
                db.save(new TestUser("Inner2"));
                return null;
            });
            return "done";
        });

        assertEquals("done", result);
    }

    @Test
    void unwrap_returnsSessionFactory() {
        assertNotNull(db.unwrap(SessionFactory.class));
    }

    @Test
    void innerExceptionCaught_outerTransactionRollsBack() {
        TestUser u = db.save(new TestUser("Alice"));
        Long id = u.getId();

        assertThrows(RuntimeException.class, () ->
                db.transaction(() -> {
                    try {
                        db.transaction(() -> {
                            TestUser inner = db.find(TestUser.class, id);
                            db.delete(inner);
                            throw new RuntimeException("inner failure");
                        });
                    } catch (RuntimeException ignored) {
                        // 用户吞掉异常
                    }
                    db.save(new TestUser("Bob"));   // 期望这行也被回滚
                })
        );

        // Alice 应该还在（内层 delete 被回滚）
        assertNotNull(db.find(TestUser.class, id));
    }

}