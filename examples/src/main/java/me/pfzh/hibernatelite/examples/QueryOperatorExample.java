package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.User;

import java.util.List;

/**
 * Category: the type-safe query DSL.
 *
 * <p>Covers every operator, ordering, and all terminal operations except
 * pagination and bulk DML (those have their own example classes).</p>
 */
public final class QueryOperatorExample {

    public static void main(String[] args) {
        ExampleSupport.run("3. QUERY DSL - operators and terminal operations",
                QueryOperatorExample::demo);
    }

    static void demo(DataStore db) {
        ExampleSupport.seedUsers(db);

        // ---------------------------------------------------------------
        ExampleSupport.step("eq / ne");
        ExampleSupport.out("eq(name, Alice)", names(db.query(User.class)
                .eq(User::getName, "Alice").list()));
        ExampleSupport.out("ne(name, Alice)", names(db.query(User.class)
                .ne(User::getName, "Alice").list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("gt / ge / lt / le");
        ExampleSupport.out("gt(age, 18)", names(db.query(User.class)
                .gt(User::getAge, 18).list()));
        ExampleSupport.out("ge(age, 22)", names(db.query(User.class)
                .ge(User::getAge, 22).list()));
        ExampleSupport.out("lt(age, 22)", names(db.query(User.class)
                .lt(User::getAge, 22).list()));
        ExampleSupport.out("le(age, 17)", names(db.query(User.class)
                .le(User::getAge, 17).list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("like / in / notIn");
        ExampleSupport.out("like(name, A%)", names(db.query(User.class)
                .like(User::getName, "A%").list()));
        ExampleSupport.out("in(name, [Alice, Bob])", names(db.query(User.class)
                .in(User::getName, List.of("Alice", "Bob")).list()));
        ExampleSupport.out("notIn(name, [Alice, Bob])", names(db.query(User.class)
                .notIn(User::getName, List.of("Alice", "Bob")).list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("isNull / isNotNull");
        db.save(new User("NoStatus", 40, null));
        ExampleSupport.out("isNull(status)", names(db.query(User.class)
                .isNull(User::getStatus).list()));
        ExampleSupport.out("isNotNull(status) count", db.query(User.class)
                .isNotNull(User::getStatus).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("between");
        ExampleSupport.out("between(age, 17, 25)", names(db.query(User.class)
                .between(User::getAge, 17, 25).list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("conditions are combined with AND");
        ExampleSupport.out("status=ACTIVE and age>18", names(db.query(User.class)
                .eq(User::getStatus, "ACTIVE")
                .gt(User::getAge, 18)
                .list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("ordering");
        ExampleSupport.out("orderByAsc(age)", ages(db.query(User.class)
                .orderByAsc(User::getAge).list()));
        ExampleSupport.out("orderByDesc(age)", ages(db.query(User.class)
                .orderByDesc(User::getAge).list()));
        ExampleSupport.out("orderByAsc(status) then orderByDesc(age)",
                names(db.query(User.class)
                        .orderByAsc(User::getStatus)
                        .orderByDesc(User::getAge)
                        .list()));

        // ---------------------------------------------------------------
        ExampleSupport.step("terminal operations: list / list(n) / one / count / exists");
        ExampleSupport.out("list().size()", db.query(User.class).list().size());
        ExampleSupport.out("list(2).size()", db.query(User.class).list(2).size());
        ExampleSupport.out("count()", db.query(User.class).count());
        ExampleSupport.out("exists(status=ACTIVE)", db.query(User.class)
                .eq(User::getStatus, "ACTIVE").exists());
        ExampleSupport.out("exists(status=GONE)", db.query(User.class)
                .eq(User::getStatus, "GONE").exists());

        ExampleSupport.out("one(name=Alice)", db.query(User.class)
                .eq(User::getName, "Alice").one());
        ExampleSupport.out("one(name=Nobody)", db.query(User.class)
                .eq(User::getName, "Nobody").one());

        try {
            db.query(User.class).eq(User::getStatus, "ACTIVE").one();
        } catch (HibernateLiteException e) {
            ExampleSupport.out("one() with several matches -> HibernateLiteException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("field validation fails fast");
        // A non-getter method reference cannot be turned into a property name:
        try {
            db.query(User.class).eq(User::toString, "x");
        } catch (HibernateLiteException e) {
            ExampleSupport.out("non-getter reference -> HibernateLiteException");
        }
        // A field that does not exist is rejected before hitting the database:
        try {
            db.query(User.class).where((cb, root) -> cb.equal(root.get("nmae"), "x")).list();
        } catch (RuntimeException e) {
            ExampleSupport.out("unknown field -> " + e.getClass().getSimpleName());
        }
    }

    private static List<String> names(List<User> users) {
        return users.stream().map(User::getName).toList();
    }

    private static List<Integer> ages(List<User> users) {
        return users.stream().map(User::getAge).toList();
    }
}
