package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.Account;
import me.pfzh.hibernatelite.examples.model.User;
import me.pfzh.hibernatelite.pagination.PageRequest;
import me.pfzh.hibernatelite.transaction.TransactionCallback;

import java.util.List;
import java.util.Map;

/**
 * Category: error handling.
 *
 * <p>One representative call per exception type, so the mapping documented in
 * the README can be checked at a glance.</p>
 */
public final class ErrorHandlingExample {

    public static void main(String[] args) {
        ExampleSupport.run("10. ERROR HANDLING", ErrorHandlingExample::demo);
    }

    static void demo(DataStore db) {
        ExampleSupport.seedUsers(db);

        // ---------------------------------------------------------------
        ExampleSupport.step("IllegalArgumentException - invalid arguments");

        show("find(null, 1L)", () -> db.find(null, 1L));
        show("find(User.class, null)", () -> db.find(User.class, null));
        show("save(null)", () -> db.save(null));
        show("delete(null)", () -> db.delete(null));
        show("query(User.class).list(0)", () -> db.query(User.class).list(0));
        show("update(empty map)", () -> db.query(User.class)
                .eq(User::getName, "Alice").update(Map.of()));
        show("PageRequest.of(-1, 10)", () -> PageRequest.of(-1, 10));
        show("PageRequest.of(0, 1001)", () -> PageRequest.of(0, 1001));
        show("PageRequest.of(MAX_VALUE, 1000)", () -> PageRequest.of(Integer.MAX_VALUE, 1000));

        // ---------------------------------------------------------------
        ExampleSupport.step("NullPointerException - Objects.requireNonNull on the facade");

        show("query((Class<?>) null)", () -> db.query((Class<User>) null));
        show("transaction((TransactionCallback) null)",
                () -> db.transaction((TransactionCallback<Object>) null));

        // ---------------------------------------------------------------
        ExampleSupport.step("HibernateLiteException - refused or invalid operations");

        show("query(User.class).delete()", () -> db.query(User.class).delete());
        show("query(User.class).update(field, value)",
                () -> db.query(User.class).update(User::getStatus, "X"));
        show("save(business-key entity)", () -> db.save(new Account("bob", 10)));
        show("update(unknown field)", () -> db.query(User.class)
                .eq(User::getName, "Alice").update(Map.of("nmae", "x")));
        show("in(empty collection)",
                () -> db.query(User.class).in(User::getName, List.of()).list());
        show("eq(field, null)",
                () -> db.query(User.class).eq(User::getName, null));
        show("non-getter method reference",
                () -> db.query(User.class).eq(User::toString, "x"));

        // ---------------------------------------------------------------
        ExampleSupport.step("IllegalStateException - value object in the wrong mode");

        var slice = db.query(User.class).page(PageRequest.of(0, 2).withoutCount());
        show("slice.totalElements()", slice::totalElements);
        show("slice.totalPages()", slice::totalPages);

        // ---------------------------------------------------------------
        ExampleSupport.step("UnsupportedOperationException - JPQL page without count");

        show("jpql.page(PageRequest)", () ->
                db.query("select u from User u", User.class).page(PageRequest.of(0, 2)));

        // ---------------------------------------------------------------
        ExampleSupport.step("NoSuchElementException-free API: empty lookups return null");
        ExampleSupport.out("find(missing)", db.find(User.class, 424242L));
        ExampleSupport.out("one(missing)", db.query(User.class).eq(User::getName, "zzz").one());
    }

    /**
     * Runs an action that is expected to fail and prints the exception it threw.
     */
    private static void show(String call, Runnable action) {
        try {
            action.run();
            ExampleSupport.out(call, "no exception (unexpected)");
        } catch (RuntimeException e) {
            ExampleSupport.out(call, e.getClass().getSimpleName());
        }
    }
}
