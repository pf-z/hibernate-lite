package me.pfzh.hibernatelite.demo;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.BusinessKeyEntity;
import me.pfzh.hibernatelite.fixture.TestUser;

import java.util.List;

public class Demo {

    public static void main(String[] args) {
        HikariDataSource ds = createDataSource();

        try (DataStore db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")
                .showSql(true)
                .build()) {

            demoBasicCrud(db);
            demoBatch(db);
            demoTransactionCommit(db);
            demoTransactionRollback(db);
            demoNestedTransaction(db);
            demoBusinessKeyRejection(db);
            demoEscapeHatch(db);

            System.out.println("\n✅ Demo 全部跑通");
        } finally {
            ds.close();
        }
    }

    // ============================================================
    // 1. 基本 CRUD
    // ============================================================

    private static void demoBasicCrud(DataStore db) {
        System.out.println("\n=== 1. 基本 CRUD ===");

        TestUser u = db.save(new TestUser("Alice"));
        System.out.println("saved id = " + u.getId());

        TestUser found = db.find(TestUser.class, u.getId());
        System.out.println("found = " + found.getName());

        db.delete(found);
        assert db.find(TestUser.class, u.getId()) == null;
        System.out.println("deleted, find returns null");
    }

    // ============================================================
    // 2. 批量保存
    // ============================================================

    private static void demoBatch(DataStore db) {
        System.out.println("\n=== 2. 批量保存 ===");

        List<TestUser> result = db.saveAll(List.of(
                new TestUser("Bob"),
                new TestUser("Charlie"),
                new TestUser("Dave")
        ));

        System.out.println("batch saved count = " + result.size());
        result.forEach(u -> System.out.println("  id=" + u.getId() + ", name=" + u.getName()));
    }

    // ============================================================
    // 3. 事务提交
    // ============================================================

    private static void demoTransactionCommit(DataStore db) {
        System.out.println("\n=== 3. 事务提交 ===");

        Long id = db.transaction(() -> {
            TestUser u = db.save(new TestUser("Eve"));
            return u.getId();
        });

        TestUser found = db.find(TestUser.class, id);
        System.out.println("transaction committed, found = " + found.getName());
    }

    // ============================================================
    // 4. 事务回滚
    // ============================================================

    private static void demoTransactionRollback(DataStore db) {
        System.out.println("\n=== 4. 事务回滚 ===");

        TestUser existing = db.save(new TestUser("Frank"));
        Long id = existing.getId();

        try {
            db.transaction(() -> {
                TestUser u = db.find(TestUser.class, id);
                db.delete(u);
                throw new RuntimeException("simulated failure");
            });
        } catch (RuntimeException e) {
            System.out.println("caught: " + e.getMessage());
        }

        // 回滚后 Frank 还在
        assert db.find(TestUser.class, id) != null;
        System.out.println("after rollback, Frank still exists");
    }

    // ============================================================
    // 5. 嵌套事务
    // ============================================================

    private static void demoNestedTransaction(DataStore db) {
        System.out.println("\n=== 5. 嵌套事务 ===");

        db.transaction(() -> {
            db.save(new TestUser("Grace"));
            db.transaction(() -> {
                db.save(new TestUser("Heidi"));
                return null;
            });
            return null;
        });

        System.out.println("nested transaction committed both");
    }

    // ============================================================
    // 6. 业务主键拒绝
    // ============================================================

    private static void demoBusinessKeyRejection(DataStore db) {
        System.out.println("\n=== 6. 业务主键拒绝 ===");

        try {
            db.save(new BusinessKeyEntity("alice"));
            System.out.println("unexpected: no exception");
        } catch (HibernateLiteException e) {
            System.out.println("rejected as expected: " + e.getMessage());
        }
    }

    // ============================================================
    // 7. escape hatch
    // ============================================================

    private static void demoEscapeHatch(DataStore db) {
        System.out.println("\n=== 7. escape hatch ===");

        var sessionFactory = db.unwrap(org.hibernate.SessionFactory.class);
        System.out.println("SessionFactory = " + sessionFactory.getClass().getSimpleName());

        try (var session = sessionFactory.openSession()) {
            Long count = session
                    .createQuery("select count(u) from TestUser u", Long.class)
                    .getSingleResult();
            System.out.println("total users = " + count);
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private static HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }
}