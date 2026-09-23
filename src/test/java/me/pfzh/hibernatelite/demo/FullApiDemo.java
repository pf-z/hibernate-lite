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
 * Hibernate-Lite 完整 API 演示。
 *
 * <p>严格按 README 的 API 分类逐项演示，覆盖所有对外用法。</p>
 *
 * <pre>
 * 目录：
 *   1.  Bootstrap & Close
 *   2.  CRUD：find / save / saveAll / delete
 *   3.  Query DSL：12 个操作符
 *   4.  Query DSL：排序
 *   5.  Query DSL：聚合（list / one / count / exists）
 *   6.  Query DSL：条件删除
 *   7.  Query DSL：条件更新
 *   8.  JPQL：参数 / 聚合 / 分页
 *   9.  Pagination：有 count / 无 count / DTO 映射
 *   10. Transaction：编程式 / 嵌套
 *   11. Escape Hatches：QuerySpec / unwrap
 *   12. Error Handling：各种异常演示
 * </pre>
 */
public class FullApiDemo {

    public static void main(String[] args) {
        HikariDataSource ds = createDataSource();

        // ============================================================
        // 1. Bootstrap & Close（用 try-with-resources 展示 AutoCloseable）
        // ============================================================
        try (DataStore db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .showSql(false)
                .ddlAuto("update")   // 演示用 update；生产用默认 "validate"
                .build()) {

            demoCrud(db);
            demoQueryOperators(db);
            demoQueryOrdering(db);
            demoQueryAggregates(db);
            demoQueryConditionalDelete(db);
            demoQueryConditionalUpdate(db);
            demoJpql(db);
            demoPaginationWithCount(db);
            demoPaginationWithoutCount(db);
            demoPaginationDtoMapping(db);
            demoTransaction(db);
            demoNestedTransaction(db);
            demoEscapeHatchQuerySpec(db);
            demoEscapeHatchUnwrap(db);
            demoErrorHandling(db);

            System.out.println("\n✅ FullApiDemo 全部跑通");

        } finally {
            ds.close();
        }
    }

    // ============================================================
    // 数据准备
    // ============================================================

    /**
     * 重置数据：清空后插入 8 条。
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
    // 2. CRUD：find / save / saveAll / delete
    // ============================================================

    private static void demoCrud(DataStore db) {
        System.out.println("\n=== 2. CRUD ===");
        seed(db);

        // --- find：按主键查询 ---
        TestUser alice = db.query(TestUser.class).eq(TestUser::getName, "Alice").one();
        TestUser found = db.find(TestUser.class, alice.getId());
        System.out.println("find: " + found.getName() + ", age=" + found.getAge());

        TestUser missing = db.find(TestUser.class, 999999L);
        System.out.println("find missing: " + missing);

        // --- save：新增（ID 为 null）---
        TestUser newUser = new TestUser("NewUser", 20, "ACTIVE");
        TestUser inserted = db.save(newUser);
        System.out.println("save insert: id=" + inserted.getId());

        // --- save：更新（接收返回值）---
        inserted.setName("NewUserUpdated");
        TestUser merged = db.save(inserted);
        System.out.println("save update: name=" + merged.getName());

        // --- saveAll：批量保存 ---
        List<TestUser> batch = db.saveAll(List.of(
                new TestUser("Batch1", 40, "ACTIVE"),
                new TestUser("Batch2", 41, "ACTIVE"),
                new TestUser("Batch3", 42, "ACTIVE")
        ));
        System.out.println("saveAll: saved " + batch.size() + " rows");

        // --- delete：删除实体 ---
        db.delete(merged);
        System.out.println("delete: after delete = "
                + db.find(TestUser.class, merged.getId()));

        // --- delete：删除游离态实体 ---
        TestUser detached = new TestUser();
        detached.setId(batch.get(0).getId());
        db.delete(detached);
        System.out.println("delete detached: after = "
                + db.find(TestUser.class, detached.getId()));
    }

    // ============================================================
    // 3. Query DSL：12 个操作符
    // ============================================================

    private static void demoQueryOperators(DataStore db) {
        System.out.println("\n=== 3. Query DSL：12 个操作符 ===");
        seed(db);

        // eq
        System.out.println("eq status=ACTIVE: " +
                names(db.query(TestUser.class).eq(TestUser::getStatus, "ACTIVE").list()));

        // ne
        System.out.println("ne name=Alice: " +
                names(db.query(TestUser.class).ne(TestUser::getName, "Alice").list()));

        // gt
        System.out.println("gt age>25: " +
                names(db.query(TestUser.class).gt(TestUser::getAge, 25).list()));

        // ge
        System.out.println("ge age>=25: " +
                names(db.query(TestUser.class).ge(TestUser::getAge, 25).list()));

        // lt
        System.out.println("lt age<20: " +
                names(db.query(TestUser.class).lt(TestUser::getAge, 20).list()));

        // le
        System.out.println("le age<=20: " +
                names(db.query(TestUser.class).le(TestUser::getAge, 20).list()));

        // like
        System.out.println("like 'A%': " +
                names(db.query(TestUser.class).like(TestUser::getName, "A%").list()));

        // in
        System.out.println("in {Alice,Bob,Eve}: " +
                names(db.query(TestUser.class)
                        .in(TestUser::getName, List.of("Alice", "Bob", "Eve"))
                        .list()));

        // notIn
        System.out.println("notIn {Alice,Bob}: " +
                names(db.query(TestUser.class)
                        .notIn(TestUser::getName, List.of("Alice", "Bob"))
                        .list()));

        // isNull
        System.out.println("isNull name: " +
                names(db.query(TestUser.class).isNull(TestUser::getName).list()));

        // isNotNull
        System.out.println("isNotNull name count: " +
                db.query(TestUser.class).isNotNull(TestUser::getName).count());

        // between
        System.out.println("between age [20,30]: " +
                names(db.query(TestUser.class).between(TestUser::getAge, 20, 30).list()));

        // 多条件 AND 组合
        System.out.println("AND status=ACTIVE AND age>20 AND age<30: " +
                names(db.query(TestUser.class)
                        .eq(TestUser::getStatus, "ACTIVE")
                        .gt(TestUser::getAge, 20)
                        .lt(TestUser::getAge, 30)
                        .list()));
    }

    // ============================================================
    // 4. Query DSL：排序
    // ============================================================

    private static void demoQueryOrdering(DataStore db) {
        System.out.println("\n=== 4. Query DSL：排序 ===");
        seed(db);

        // 单字段升序
        System.out.println("orderByAsc age: " +
                names(db.query(TestUser.class).orderByAsc(TestUser::getAge).list()));

        // 单字段降序
        System.out.println("orderByDesc age: " +
                names(db.query(TestUser.class).orderByDesc(TestUser::getAge).list()));

        // 多字段排序
        System.out.println("orderBy status ASC, age DESC: " +
                names(db.query(TestUser.class)
                        .orderByAsc(TestUser::getStatus)
                        .orderByDesc(TestUser::getAge)
                        .list()));
    }

    // ============================================================
    // 5. Query DSL：聚合（list / one / count / exists）
    // ============================================================

    private static void demoQueryAggregates(DataStore db) {
        System.out.println("\n=== 5. Query DSL：聚合 ===");
        seed(db);

        // list() —— 默认上限 1000
        List<TestUser> all = db.query(TestUser.class).list();
        System.out.println("list(): " + all.size() + " rows");

        // list(int limit) —— 指定上限
        List<TestUser> top3 = db.query(TestUser.class)
                .orderByDesc(TestUser::getAge)
                .list(3);
        System.out.println("list(3) top age: " + names(top3));

        // one() —— 单条查询
        TestUser alice = db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .one();
        System.out.println("one Alice: age=" + (alice == null ? null : alice.getAge()));

        TestUser none = db.query(TestUser.class)
                .eq(TestUser::getName, "Nonexistent")
                .one();
        System.out.println("one nonexistent: " + none);

        // count()
        long count = db.query(TestUser.class).eq(TestUser::getStatus, "ACTIVE").count();
        System.out.println("count ACTIVE: " + count);

        // exists()
        boolean exists = db.query(TestUser.class).eq(TestUser::getName, "Alice").exists();
        System.out.println("exists Alice: " + exists);

        boolean notExists = db.query(TestUser.class).eq(TestUser::getName, "Zed").exists();
        System.out.println("exists Zed: " + notExists);
    }

    // ============================================================
    // 6. Query DSL：条件删除
    // ============================================================

    private static void demoQueryConditionalDelete(DataStore db) {
        System.out.println("\n=== 6. Query DSL：条件删除 ===");
        seed(db);

        // 条件删除（单条 SQL）
        int deleted = db.query(TestUser.class)
                .lt(TestUser::getAge, 18)
                .delete();
        System.out.println("delete age<18: " + deleted + " rows");

        // 逃生舱 + 条件删除
        int deleted2 = db.query(TestUser.class)
                .gt(TestUser::getAge, 0)
                .where((cb, root) -> cb.equal(root.get("status"), "INACTIVE"))
                .delete();
        System.out.println("delete status=INACTIVE (via where): " + deleted2 + " rows");

        System.out.println("remaining: " + db.query(TestUser.class).count());
    }

    // ============================================================
    // 7. Query DSL：条件更新
    // ============================================================

    private static void demoQueryConditionalUpdate(DataStore db) {
        System.out.println("\n=== 7. Query DSL：条件更新 ===");
        seed(db);

        // 单字段更新
        int updated1 = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .lt(TestUser::getAge, 18)
                .update(TestUser::getStatus, "MINOR");
        System.out.println("update ACTIVE & age<18 → MINOR: " + updated1 + " rows");

        // 多字段更新
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

        // 逃生舱 + 条件更新
        int updated3 = db.query(TestUser.class)
                .where((cb, root) -> cb.lt(root.get("age"), 20))
                .update(TestUser::getStatus, "TODO");
        System.out.println("update age<20 (via where) → TODO: " + updated3 + " rows");
    }

    // ============================================================
    // 8. JPQL：参数 / 聚合 / 分页
    // ============================================================

    private static void demoJpql(DataStore db) {
        System.out.println("\n=== 8. JPQL ===");
        seed(db);

        // 基础查询
        List<TestUser> all = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .list();
        System.out.println("JPQL all: " + names(all));

        // 单参数
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
        System.out.println("JPQL one Alice: age="
                + (alice == null ? null : alice.getAge()));

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

        // 分页
        Page<TestUser> page = db.query(
                        "SELECT u FROM TestUser u ORDER BY u.age",
                        TestUser.class)
                .page(
                        "SELECT COUNT(u) FROM TestUser u",
                        PageRequest.of(0, 3));
        System.out.println("JPQL page 0: total=" + page.totalElements()
                + ", content=" + names(page.content()));

        // 无 count 分页
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
    // 9. Pagination：有 count / 无 count / DTO 映射
    // ============================================================

    private static void demoPaginationWithCount(DataStore db) {
        System.out.println("\n=== 9a. Pagination：有 count ===");
        seed(db);

        // 第一页
        Page<TestUser> page0 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3));
        printPage(page0);

        // 最后一页
        Page<TestUser> page2 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(2, 3));
        printPage(page2);

        // 带条件的分页
        Page<TestUser> active = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .orderByDesc(TestUser::getAge)
                .page(PageRequest.of(0, 3));
        System.out.println("with condition status=ACTIVE:");
        printPage(active);
    }

    private static void demoPaginationWithoutCount(DataStore db) {
        System.out.println("\n=== 9b. Pagination：无 count ===");
        seed(db);

        // Slice 模式，hasNext 仍然准确
        Page<TestUser> slice0 = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3).withoutCount());
        System.out.println("slice 0: hasTotal=" + slice0.hasTotal()
                + ", hasNext=" + slice0.hasNext()
                + ", content=" + names(slice0.content()));

        // 最后一页恰好满页 —— hasNext 仍准确
        Page<TestUser> sliceLast = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(1, 4).withoutCount());
        System.out.println("slice 1 (exactly last): hasNext=" + sliceLast.hasNext()
                + ", isLast=" + sliceLast.isLast()
                + ", content=" + names(sliceLast.content()));

        // totalElements 在 slice 模式会抛异常
        try {
            slice0.totalElements();
        } catch (IllegalStateException e) {
            System.out.println("totalElements in slice mode: " + e.getMessage());
        }
    }

    private static void demoPaginationDtoMapping(DataStore db) {
        System.out.println("\n=== 9c. Pagination：DTO 映射 ===");
        seed(db);

        Page<TestUser> userPage = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 3));

        // 保留分页元数据
        Page<String> namePage = userPage.map(TestUser::getName);
        System.out.println("mapped content: " + namePage.content());
        System.out.println("mapped totalElements: " + namePage.totalElements());
        System.out.println("mapped hasNext: " + namePage.hasNext());
    }

    // ============================================================
    // 10. Transaction：编程式 / 嵌套
    // ============================================================

    private static void demoTransaction(DataStore db) {
        System.out.println("\n=== 10a. Transaction：编程式 ===");
        seed(db);

        // 有返回值
        Long id = db.transaction(() -> {
            TestUser u = db.save(new TestUser("TxCommit", 50, "ACTIVE"));
            return u.getId();
        });
        System.out.println("commit with return: id=" + id);

        // 无返回值
        db.transaction(() -> {
            db.save(new TestUser("TxNoReturn", 51, "ACTIVE"));
        });
        System.out.println("commit without return: "
                + db.query(TestUser.class).eq(TestUser::getName, "TxNoReturn").count());

        // 回滚
        TestUser existing = db.save(new TestUser("TxRollback", 60, "ACTIVE"));
        Long rollbackId = existing.getId();
        try {
            db.transaction(() -> {
                db.delete(db.find(TestUser.class, rollbackId));
                throw new RuntimeException("simulated failure");
            });
        } catch (RuntimeException e) {
            System.out.println("caught: " + e.getMessage());
        }
        System.out.println("after rollback still exists: "
                + (db.find(TestUser.class, rollbackId) != null));
    }

    private static void demoNestedTransaction(DataStore db) {
        System.out.println("\n=== 10b. Transaction：嵌套 ===");
        seed(db);

        // 嵌套提交
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
        System.out.println("nested commit all saved: " + (count == 3));

        // 内层异常被吞掉，外层仍回滚
        seed(db);
        try {
            db.transaction(() -> {
                db.save(new TestUser("WillRollback", 80, "ACTIVE"));
                try {
                    db.transaction(() -> {
                        throw new RuntimeException("inner failure");
                    });
                } catch (RuntimeException ignored) {
                    System.out.println("inner swallowed");
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
        System.out.println("all rolled back: " + (rolledBack == 0));
    }

    // ============================================================
    // 11. Escape Hatches：QuerySpec / unwrap
    // ============================================================

    private static void demoEscapeHatchQuerySpec(DataStore db) {
        System.out.println("\n=== 11a. Escape Hatch：QuerySpec ===");
        seed(db);

        // OR 嵌套
        List<TestUser> aOrB = db.query(TestUser.class)
                .where((cb, root) -> cb.or(
                        cb.like(root.get("name"), "A%"),
                        cb.like(root.get("name"), "B%")
                ))
                .list();
        System.out.println("where(or(like A%, like B%)): " + names(aOrB));

        // DSL + QuerySpec 混合
        List<TestUser> combined = db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .where((cb, root) -> cb.gt(root.get("age"), 25))
                .list();
        System.out.println("DSL status=ACTIVE + where(age>25): " + names(combined));

        // 多个 where 都 AND 组合
        List<TestUser> multiSpec = db.query(TestUser.class)
                .where((cb, root) -> cb.gt(root.get("age"), 20))
                .where((cb, root) -> cb.lt(root.get("age"), 30))
                .list();
        System.out.println("where(age>20).where(age<30): " + names(multiSpec));
    }

    private static void demoEscapeHatchUnwrap(DataStore db) {
        System.out.println("\n=== 11b. Escape Hatch：unwrap ===");
        seed(db);

        var sessionFactory = db.unwrap(org.hibernate.SessionFactory.class);
        System.out.println("unwrap SessionFactory: "
                + sessionFactory.getClass().getSimpleName());

        // 用原生 Hibernate 做查询
        try (var session = sessionFactory.openSession()) {
            Long count = session
                    .createQuery("SELECT COUNT(u) FROM TestUser u", Long.class)
                    .getSingleResult();
            System.out.println("native count: " + count);
        }
    }

    // ============================================================
    // 12. Error Handling：各种异常演示
    // ============================================================

    private static void demoErrorHandling(DataStore db) {
        System.out.println("\n=== 12. Error Handling ===");
        seed(db);

        // 1. 无条件删除
        try {
            db.query(TestUser.class).delete();
        } catch (HibernateLiteException e) {
            System.out.println("unconditional delete: " + e.getMessage());
        }

        // 2. 无条件更新
        try {
            db.query(TestUser.class).update(TestUser::getStatus, "X");
        } catch (HibernateLiteException e) {
            System.out.println("unconditional update: " + e.getMessage());
        }

        // 3. 字段不存在
        try {
            db.query(TestUser.class)
                    .eq(TestUser::getName, "Alice")
                    .update(Map.of("nonexistentField", "x"));
        } catch (HibernateLiteException e) {
            System.out.println("invalid field: " + e.getMessage());
        }

        // 4. 分页 page 为负
        try {
            PageRequest.of(-1, 10);
        } catch (IllegalArgumentException e) {
            System.out.println("negative page: " + e.getMessage());
        }

        // 5. 分页 size 超限
        try {
            PageRequest.of(0, 2000);
        } catch (IllegalArgumentException e) {
            System.out.println("size too large: " + e.getMessage());
        }

        // 6. Slice 模式调 totalElements()
        try {
            Page<TestUser> slice = db.query(TestUser.class)
                    .page(PageRequest.of(0, 3).withoutCount());
            slice.totalElements();
        } catch (IllegalStateException e) {
            System.out.println("slice totalElements: " + e.getMessage());
        }

        // 7. unwrap 不支持的类型
        try {
            db.unwrap(String.class);
        } catch (HibernateLiteException e) {
            System.out.println("unsupported unwrap: " + e.getMessage());
        }

        // 8. JPQL 语法错误
        try {
            db.query("SELECT x FROM NonExistentEntity x", TestUser.class).list();
        } catch (RuntimeException e) {
            System.out.println("invalid JPQL: " + e.getClass().getSimpleName());
        }

        // 9. JPQL 单参数 page
        try {
            db.query("SELECT u FROM TestUser u", TestUser.class)
                    .page(PageRequest.of(0, 3));
        } catch (UnsupportedOperationException e) {
            System.out.println("jpql page without count: " + e.getMessage());
        }

        // 10. one() 多条匹配
        try {
            db.query(TestUser.class)
                    .eq(TestUser::getStatus, "ACTIVE")
                    .one();
        } catch (HibernateLiteException e) {
            System.out.println("one multiple matches: " + e.getMessage());
        }
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
                + ", hasPrevious=" + page.hasPrevious()
                + ", isFirst=" + page.isFirst()
                + ", isLast=" + page.isLast()
                + " | " + names(page.content()));
    }

    private static List<String> names(List<TestUser> users) {
        return users.stream().map(TestUser::getName).toList();
    }

    private static HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:full-api-demo;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }
}