package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.Purchase;
import me.pfzh.hibernatelite.examples.model.User;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

import java.util.List;

/**
 * Category: JPQL.
 *
 * <p>Covers named parameters, all terminal operations, projections, joins,
 * aggregation and both pagination modes.</p>
 */
public final class JpqlExample {

    public static void main(String[] args) {
        ExampleSupport.run("6. JPQL", JpqlExample::demo);
    }

    static void demo(DataStore db) {
        List<User> users = ExampleSupport.seedUsers(db);
        User alice = users.get(0);
        db.saveAll(List.of(
                new Purchase("Book", alice),
                new Purchase("Pen", alice),
                new Purchase("Lamp", users.get(1))));

        // ---------------------------------------------------------------
        ExampleSupport.step("list() with named parameters");
        List<User> active = db.query(
                        "select u from User u where u.status = :status and u.age > :minAge order by u.age",
                        User.class)
                .param("status", "ACTIVE")
                .param("minAge", 18)
                .list();
        ExampleSupport.out("result", active.stream().map(User::getName).toList());

        // ---------------------------------------------------------------
        ExampleSupport.step("list(limit)");
        ExampleSupport.out("list(2).size()", db.query(
                "select u from User u order by u.age", User.class).list(2).size());

        // ---------------------------------------------------------------
        ExampleSupport.step("one(): 0 -> null, 1 -> entity, >1 -> exception");
        ExampleSupport.out("one(Alice)", db.query(
                        "select u from User u where u.name = :n", User.class)
                .param("n", "Alice").one());
        ExampleSupport.out("one(Nobody)", db.query(
                        "select u from User u where u.name = :n", User.class)
                .param("n", "Nobody").one());
        try {
            db.query("select u from User u where u.status = :s", User.class)
                    .param("s", "ACTIVE").one();
        } catch (HibernateLiteException e) {
            ExampleSupport.out("one() with several matches -> HibernateLiteException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("count() and exists(): the JPQL must be SELECT COUNT(...)");
        long count = db.query(
                        "select count(u) from User u where u.status = :s", Long.class)
                .param("s", "ACTIVE")
                .count();
        ExampleSupport.out("count(ACTIVE)", count);
        ExampleSupport.out("count(empty result)", db.query(
                        "select count(u) from User u where u.name = :n", Long.class)
                .param("n", "Nobody").count());
        ExampleSupport.out("exists(Alice)", db.query(
                        "select u from User u where u.name = :n", User.class)
                .param("n", "Alice").exists());

        // ---------------------------------------------------------------
        ExampleSupport.step("scalar projection into Object[]");
        List<Object[]> rows = db.query(
                        "select u.name, u.age from User u order by u.age", Object[].class)
                .list();
        rows.forEach(row -> ExampleSupport.out("row", row[0] + " / " + row[1]));

        // ---------------------------------------------------------------
        ExampleSupport.step("GROUP BY with an aggregate");
        List<Object[]> grouped = db.query(
                        "select u.status, count(u) from User u group by u.status", Object[].class)
                .list();
        grouped.forEach(row -> ExampleSupport.out("status/count", row[0] + " / " + row[1]));

        // ---------------------------------------------------------------
        ExampleSupport.step("JOIN across an association");
        List<Purchase> purchases = db.query(
                        "select p from Purchase p join p.user u where u.name = :n",
                        Purchase.class)
                .param("n", "Alice")
                .list();
        ExampleSupport.out("purchases of Alice",
                purchases.stream().map(Purchase::getProduct).toList());

        // ---------------------------------------------------------------
        ExampleSupport.step("page(countJpql, request): total-count mode");
        Page<User> page = db.query(
                        "select u from User u order by u.age", User.class)
                .page("select count(u) from User u", PageRequest.of(0, 2));
        ExampleSupport.out("content", page.content().stream().map(User::getName).toList());
        ExampleSupport.out("totalElements", page.totalElements());
        ExampleSupport.out("totalPages", page.totalPages());

        // The count statement may declare a different subset of the parameters:
        ExampleSupport.step("page(...): page and count statements with different parameters");
        Page<User> mixed = db.query(
                        "select u from User u where u.status = :status order by abs(u.age - :pivot)",
                        User.class)
                .param("status", "ACTIVE")
                .param("pivot", 22)
                .page("select count(u) from User u where u.status = :status",
                        PageRequest.of(0, 2));
        ExampleSupport.out("content", mixed.content().stream().map(User::getName).toList());
        ExampleSupport.out("totalElements", mixed.totalElements());

        // ---------------------------------------------------------------
        ExampleSupport.step("page(null, request.withoutCount()): slice mode");
        Page<User> slice = db.query("select u from User u order by u.age", User.class)
                .page(null, PageRequest.of(0, 2).withoutCount());
        ExampleSupport.out("hasTotal", slice.hasTotal());
        ExampleSupport.out("hasNext", slice.hasNext());

        // ---------------------------------------------------------------
        ExampleSupport.step("page(PageRequest) without a count statement is unsupported");
        try {
            db.query("select u from User u", User.class).page(PageRequest.of(0, 2));
        } catch (UnsupportedOperationException e) {
            ExampleSupport.out("-> UnsupportedOperationException");
            ExampleSupport.out("message", e.getMessage());
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("errors: blank JPQL, stray parameter, syntax error");
        try {
            db.query("   ", User.class);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("blank jpql -> IllegalArgumentException");
        }
        try {
            db.query("select u from User u", User.class).param("unused", 1).list();
        } catch (RuntimeException e) {
            ExampleSupport.out("stray parameter -> " + e.getClass().getSimpleName());
        }
        try {
            db.query("selec u frm User u", User.class).list();
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("syntax error -> IllegalArgumentException");
        }
    }
}
