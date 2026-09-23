package me.pfzh.hibernatelite.demo;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

import java.util.List;
import java.util.Map;

/**
 * Hibernate-Lite 完整功能演示。
 *
 * <p>覆盖：</p>
 * <ul>
 *     <li>基本 CRUD</li>
 *     <li>Query DSL：全部操作符</li>
 *     <li>排序 + 聚合</li>
 *     <li>条件删除 + 安全保护</li>
 *     <li>条件更新（单字段 / 多字段 / 逃生舱）</li>
 *     <li>JPQL 查询（参数 / JOIN / 分页）</li>
 *     <li>分页（有 count / 无 count）</li>
 *     <li>逃生舱</li>
 *     <li>事务（提交 / 回滚 / 嵌套）</li>
 * </ul>
 */
public class QueryDemo {

    public static void main(String[] args) {
        HikariDataSource ds = createDataSource();

        try (DataStore db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")
                .showSql(false)
                .build()) {

            demoCrud(db);
            demoQueryOperators(db);
            demoOrderBy(db);
            demoAggregates(db);
            demoConditionalDelete(db);
            demoConditionalUpdate(db);
            demoJpql(db);
            demoPaginationWithCount(db);
            demoPaginationWithoutCount(db);
            demoEscapeHatch(db);
            demoTransaction(db);
            demoNestedTransaction(db);

            System.out.println("\n✅ QueryDemo 全部跑通");

        } finally {
            ds.close();
        }
    }

    // ============================================================
    // 0. 数据准备
    // ============================================================

    /**
     * 插入 8 条测试数据。
     *
     * <pre>
     * Name     | Age | Status
     * ---------|-----|---------
     * Alice    | 25  | ACTIVE
     * Bob      | 17  | ACTIVE
     * Charlie  | 30  | ACTIVE
     * Dave     | 15  | INACTIVE
     * Eve      | 22  | ACTIVE
     * Frank    | 45  | INACTIVE
     * Grace    | 28  | ACTIVE
     * Heidi    | 35  | ACTIVE
     * </pre>
     */
    private static void seed(DataStore db) {
        if (db.query(TestUser.class).count() > 0) {
            db.query(TestUser.class).isNotNull(TestUser::getName).delete();
        }

        db.saveAll(List.of(
                new TestUser("Alice", 25, "ACTIVE"),
                new TestUser("Bob", 17, "ACTIVE"),
                new TestUser("Charlie", 30, "ACTIVE"),
                new TestUser("Dave", 15, "INACTIVE"),
                new TestUser("Eve", 22, "ACTIVE"),
                new TestUser("Frank", 45, "INACTIVE"),
                new TestUser("Grace", 28, "ACTIVE"),
                new TestUser("Heidi", 35, "ACTIVE")
        ));
    }

    // ============================================================
    // 1. 基本 CRUD
    // ============================================================

    private static void demoCrud(DataStore db) {
        System.out.println("\n=== 1. 基本 CRUD ===");

        TestUser u = db.save(new TestUser("TempUser", 20, "ACTIVE"));
        System.out.println("save: id=" + u.getId() + ", name=" + u.getName());

        TestUser found = db.find(TestUser.class, u.getId());
        System.out.println("find: name=" + found.getName());

        db.delete(found);
        System.out.println("delete: find after delete = " + db.find(TestUser.class, u.getId()));
    }

    // ============================================================
    // 2. Query DSL：全部操作符
    // ============================================================

    private static void demoQueryOperators(DataStore db) {
        System.out.println("\n=== 2. Query DSL：操作符 ===");
        seed(db);

        List<TestUser> active = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .list();
        System.out.println("eq status=ACTIVE: " + names(active));

        List<TestUser> notAlice = db.query(TestUser.class)
                .ne(TestUser::getName, "Alice")
                .list();
        System.out.println("ne name=Alice: " + names(notAlice));

        List<TestUser> over18 = db.query(TestUser.class)
                .gt(TestUser::getAge, 18)
                .list();
        System.out.println("gt age>18: " + names(over18));

        List<TestUser> atLeast25 = db.query(TestUser.class)
                .ge(TestUser::getAge, 25)
                .list();
        System.out.println("ge age>=25: " + names(atLeast25));

        List<TestUser> under25 = db.query(TestUser.class)
                .lt(TestUser::getAge, 25)
                .list();
        System.out.println("lt age<25: " + names(under25));

        List<TestUser> atMost25 = db.query(TestUser.class)
                .le(TestUser::getAge, 25)
                .list();
        System.out.println("le age<=25: " + names(atMost25));

        List<TestUser> startsWithA = db.query(TestUser.class)
                .like(TestUser::getName, "A%")
                .list();
        System.out.println("like 'A%': " + names(startsWithA));

        List<TestUser> inList = db.query(TestUser.class)
                .in(TestUser::getName, List.of("Alice", "Bob", "Eve"))
                .list();
        System.out.println("in {Alice,Bob,Eve}: " + names(inList));

        List<TestUser> notInList = db.query(TestUser.class)
                .notIn(TestUser::getName, List.of("Alice", "Bob"))
                .list();
        System.out.println("notIn {Alice,Bob}: " + names(notInList));

        List<TestUser> between = db.query(TestUser.class)
                .between(TestUser::getAge, 20, 30)
                .list();
        System.out.println("between [20,30]: " + names(between));

        List<TestUser> multi = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .gt(TestUser::getAge, 20)
                .lt(TestUser::getAge, 30)
                .list();
        System.out.println("AND status=ACTIVE, 20<age<30: " + names(multi));
    }

    // ============================================================
    // 3. 排序
    // ============================================================

    private static void demoOrderBy(DataStore db) {
        System.out.println("\n=== 3. 排序 ===");
        seed(db);

        List<TestUser> byAgeAsc = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .list();
        System.out.println("orderByAsc age: " + names(byAgeAsc));

        List<TestUser> byAgeDesc = db.query(TestUser.class)
                .orderByDesc(TestUser::getAge)
                .list();
        System.out.println("orderByDesc age: " + names(byAgeDesc));

        List<TestUser> multiOrder = db.query(TestUser.class)
                .orderByAsc(TestUser::getStatus)
                .orderByDesc(TestUser::getAge)
                .list();
        System.out.println("orderBy status ASC, age DESC: " + names(multiOrder));
    }

    // ============================================================
    // 4. 聚合
    // ============================================================

    private static void demoAggregates(DataStore db) {
        System.out.println("\n=== 4. 聚合 ===");
        seed(db);

        long total = db.query(TestUser.class).count();
        System.out.println("count all: " + total);

        long activeCount = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .count();
        System.out.println("count status=ACTIVE: " + activeCount);

        boolean hasAlice = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .exists();
        System.out.println("exists Alice: " + hasAlice);

        boolean hasZed = db.query(TestUser.class)
                .eq(TestUser::getName, "Zed")
                .exists();
        System.out.println("exists Zed: " + hasZed);

        TestUser alice = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .one();
        System.out.println("one Alice: age=" + (alice == null ? null : alice.getAge()));

        TestUser none = db.query(TestUser.class)
                .eq(TestUser::getName, "Zed")
                .one();
        System.out.println("one Zed: " + none);

        List<TestUser> top3 = db.query(TestUser.class)
                .orderByDesc(TestUser::getAge)
                .list(3);
        System.out.println("list(3) top by age: " + names(top3));
    }

    // ============================================================
    // 5. 条件删除
    // ============================================================

    private static void demoConditionalDelete(DataStore db) {
        System.out.println("\n=== 5. 条件删除 ===");
        seed(db);

        int deleted = db.query(TestUser.class)
                .lt(TestUser::getAge, 18)
                .delete();
        System.out.println("delete age<18: " + deleted + " rows");

        long remaining = db.query(TestUser.class).count();
        System.out.println("remaining: " + remaining);

        // 安全保护：无条件删除会抛异常
        try {
            db.query(TestUser.class).delete();
            System.out.println("unexpected: no exception");
        } catch (HibernateLiteException e) {
            System.out.println("unconditional delete rejected: " + e.getMessage());
        }

        // 逃生舱 + 条件删除
        int deleted2 = db.query(TestUser.class)
                .gt(TestUser::getAge, 0)
                .where((cb, root) -> cb.equal(root.get("status"), "INACTIVE"))
                .delete();
        System.out.println("delete status=INACTIVE (via where): " + deleted2 + " rows");
    }

    // ============================================================
    // 6. 条件更新
    // ============================================================

    private static void demoConditionalUpdate(DataStore db) {
        System.out.println("\n=== 6. 条件更新 ===");
        seed(db);

        // 单字段更新：所有 ACTIVE 且 age < 18 的用户 → MINOR
        int updated = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .lt(TestUser::getAge, 18)
                .update(TestUser::getStatus, "MINOR");
        System.out.println("update ACTIVE & age<18 → MINOR: " + updated + " rows");

        long minors = db.query(TestUser.class)
                .eq(TestUser::getStatus, "MINOR")
                .count();
        System.out.println("count MINOR: " + minors);

        // 多字段更新：Charlie → age=31, status=VIP
        int updated2 = db.query(TestUser.class)
                .eq(TestUser::getName, "Charlie")
                .update(Map.of(
                        "age", 31,
                        "status", "VIP"
                ));
        System.out.println("update Charlie → {age=31, status=VIP}: " + updated2 + " rows");

        TestUser charlie = db.query(TestUser.class)
                .eq(TestUser::getName, "Charlie")
                .one();
        System.out.println("Charlie now: age=" + charlie.getAge()
                + ", status=" + charlie.getStatus());

        // 逃生舱 + 条件更新：所有 age < 20 → status=TODO
        int updated3 = db.query(TestUser.class)
                .where((cb, root) -> cb.lt(root.get("age"), 20))
                .update(TestUser::getStatus, "TODO");
        System.out.println("update age<20 (via where) → TODO: " + updated3 + " rows");

        // 安全保护：无条件更新会抛异常
        try {
            db.query(TestUser.class).update(TestUser::getStatus, "ALL");
            System.out.println("unexpected: no exception");
        } catch (HibernateLiteException e) {
            System.out.println("unconditional update rejected: " + e.getMessage());
        }
    }

    // ============================================================
    // 7. JPQL 查询
    // ============================================================

    private static void demoJpql(DataStore db) {
        System.out.println("\n=== 7. JPQL 查询 ===");
        seed(db);

        // 基础查询
        List<TestUser> all = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .list();
        System.out.println("JPQL all: " + names(all));

        // 带命名参数
        List<TestUser> active = db.query(
                        "SELECT u FROM TestUser u WHERE u.status = :status ORDER BY u.age",
                        TestUser.class)
                .param("status", "ACTIVE")
                .list();
        System.out.println("JPQL status=:status: " + names(active));

        // 多参数
        List<TestUser> filtered = db.query(
                        "SELECT u FROM TestUser u WHERE u.status = :status AND u.age > :minAge",
                        TestUser.class)
                .param("status", "ACTIVE")
                .param("minAge", 20)
                .list();
        System.out.println("JPQL status=ACTIVE AND age>20: " + names(filtered));

        // 单条
        TestUser alice = db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Alice")
                .one();
        System.out.println("JPQL one Alice: age=" + (alice == null ? null : alice.getAge()));

        // 计数
        long count = db.query(
                        "SELECT COUNT(u) FROM TestUser u WHERE u.status = :status",
                        Long.class)
                .param("status", "ACTIVE")
                .count();
        System.out.println("JPQL count ACTIVE: " + count);

        // 存在
        boolean exists = db.query(
                        "SELECT u FROM TestUser u WHERE u.name = :name",
                        TestUser.class)
                .param("name", "Alice")
                .exists();
        System.out.println("JPQL exists Alice: " + exists);

        // 分页（有 count）
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(0, 3));
        System.out.println("JPQL page 0: total=" + page.totalElements()
                + ", content=" + names(page.content()));

        // 分页（无 count）
        Page<TestUser> slice = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(0, 3).withoutCount());
        System.out.println("JPQL slice: hasNext=" + slice.hasNext()
                + ", content=" + names(slice.content()));
    }

    // ============================================================
    // 8. 分页（有 count）
    // ============================================================

    private static void demoPaginationWithCount(DataStore db) {
        System.out.println("\n=== 8. 分页（有 count） ===");
        seed(db);

        Page<TestUser> page0 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3));
        printPage(page0);

        Page<TestUser> page1 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(1, 3));
        printPage(page1);

        Page<TestUser> page2 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(2, 3));
        printPage(page2);

        Page<TestUser> activePage = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .orderByDesc(TestUser::getAge)
                .page(PageRequest.of(0, 3));

        System.out.println("page with condition status=ACTIVE:");
        printPage(activePage);

        Page<String> namesPage = activePage.map(TestUser::getName);
        System.out.println("mapped to names: " + namesPage.content());
    }

    // ============================================================
    // 9. 分页（无 count，无限滚动）
    // ============================================================

    private static void demoPaginationWithoutCount(DataStore db) {
        System.out.println("\n=== 9. 分页（无 count） ===");
        seed(db);

        Page<TestUser> slice0 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3).withoutCount());

        System.out.println("slice 0: content=" + names(slice0.content())
                + ", hasTotal=" + slice0.hasTotal()
                + ", hasNext=" + slice0.hasNext());

        // 最后一页恰好满页
        Page<TestUser> slice1 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(1, 4).withoutCount());

        System.out.println("slice 1 (last, exactly full): content=" + names(slice1.content())
                + ", hasNext=" + slice1.hasNext()
                + ", isLast=" + slice1.isLast());

        try {
            slice0.totalElements();
            System.out.println("unexpected: no exception");
        } catch (IllegalStateException e) {
            System.out.println("totalElements not available in slice mode: "
                    + e.getMessage());
        }
    }

    // ============================================================
    // 10. 逃生舱
    // ============================================================

    private static void demoEscapeHatch(DataStore db) {
        System.out.println("\n=== 10. 逃生舱 ===");
        seed(db);

        List<TestUser> startsWithAOrB = db.query(TestUser.class)
                .where((cb, root) -> cb.or(
                        cb.like(root.get("name"), "A%"),
                        cb.like(root.get("name"), "B%")
                ))
                .list();
        System.out.println("where(or(like A%, like B%)): " + names(startsWithAOrB));

        List<TestUser> combined = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .where((cb, root) -> cb.gt(root.get("age"), 25))
                .list();
        System.out.println("eq status + where(age>25): " + names(combined));

        var sessionFactory = db.unwrap(org.hibernate.SessionFactory.class);
        System.out.println("unwrap SessionFactory: "
                + sessionFactory.getClass().getSimpleName());
    }

    // ============================================================
    // 11. 事务（提交 / 回滚）
    // ============================================================

    private static void demoTransaction(DataStore db) {
        System.out.println("\n=== 11. 事务 ===");
        seed(db);

        Long id = db.transaction(() -> {
            TestUser u = db.save(new TestUser("TxCommit", 50, "ACTIVE"));
            return u.getId();
        });
        System.out.println("commit: saved id=" + id
                + ", found=" + (db.find(TestUser.class, id) != null));

        TestUser existing = db.save(new TestUser("TxRollback", 60, "ACTIVE"));
        Long rollbackId = existing.getId();

        try {
            db.transaction(() -> {
                db.delete(db.find(TestUser.class, rollbackId));
                throw new RuntimeException("simulated failure");
            });
        } catch (RuntimeException e) {
            System.out.println("rollback triggered: " + e.getMessage());
        }

        System.out.println("after rollback: still exists = "
                + (db.find(TestUser.class, rollbackId) != null));
    }

    // ============================================================
    // 12. 嵌套事务
    // ============================================================

    private static void demoNestedTransaction(DataStore db) {
        System.out.println("\n=== 12. 嵌套事务 ===");
        seed(db);

        db.transaction(() -> {
            db.save(new TestUser("Outer1", 70, "ACTIVE"));
            db.transaction(() -> {
                db.save(new TestUser("Inner1", 71, "ACTIVE"));
                return null;
            });
            db.save(new TestUser("Outer2", 72, "ACTIVE"));
            return null;
        });

        long count = db.query(TestUser.class)
                .in(TestUser::getName, List.of("Outer1", "Inner1", "Outer2"))
                .count();
        System.out.println("nested commit: all 3 saved = " + (count == 3));

        seed(db);
        try {
            db.transaction(() -> {
                db.save(new TestUser("WillRollback", 80, "ACTIVE"));
                try {
                    db.transaction(() -> {
                        throw new RuntimeException("inner failure");
                    });
                } catch (RuntimeException ignored) {
                    System.out.println("inner exception swallowed by user");
                }
                db.save(new TestUser("AlsoRollback", 81, "ACTIVE"));
                return null;
            });
        } catch (HibernateLiteException e) {
            System.out.println("outer commit rejected: " + e.getMessage());
        }

        long rolledBack = db.query(TestUser.class)
                .in(TestUser::getName, List.of("WillRollback", "AlsoRollback"))
                .count();
        System.out.println("inner failure propagated: rolled back = " + (rolledBack == 0));
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private static void printPage(Page<TestUser> page) {
        System.out.println("  page=" + page.page()
                + ", size=" + page.size()
                + ", elements=" + page.numberOfElements()
                + ", total=" + page.totalElements()
                + ", totalPages=" + page.totalPages()
                + ", hasNext=" + page.hasNext()
                + ", hasPrev=" + page.hasPrevious()
                + ", isFirst=" + page.isFirst()
                + ", isLast=" + page.isLast()
                + " | " + names(page.content()));
    }

    private static List<String> names(List<TestUser> users) {
        return users.stream().map(TestUser::getName).toList();
    }

    private static HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:query-demo;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }
}