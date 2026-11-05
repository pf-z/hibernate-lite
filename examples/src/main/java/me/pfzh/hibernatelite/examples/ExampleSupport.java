package me.pfzh.hibernatelite.examples;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.examples.model.Account;
import me.pfzh.hibernatelite.examples.model.Purchase;
import me.pfzh.hibernatelite.examples.model.User;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared plumbing for every example.
 *
 * <p>Each example gets a fresh in-memory H2 database, a fresh
 * {@link DataStore}, and a console section header. Nothing here is part of the
 * library API - it only keeps the example classes focused on what they
 * demonstrate.</p>
 */
public final class ExampleSupport {

    private ExampleSupport() {
    }

    /** Every entity the examples need, registered once. */
    private static final Class<?>[] ENTITIES = {User.class, Purchase.class, Account.class};

    /**
     * Builds a DataStore, runs the example body, and closes everything.
     *
     * @param title section header printed before the body
     * @param body  the example itself
     */
    public static void run(String title, Consumer<DataStore> body) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:example-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setMaximumPoolSize(4);

        section(title);
        try (DataStore db = HibernateLite.builder()
                .dataSource(ds)
                .entities(ENTITIES)
                .showSql(false)
                .ddlAuto("create-drop")
                .build()) {
            body.accept(db);
        } finally {
            ds.close();
        }
    }

    // ------------------------------------------------------------------
    // console helpers
    // ------------------------------------------------------------------

    public static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(72));
        System.out.println("  " + title);
        System.out.println("=".repeat(72));
    }

    public static void step(String text) {
        System.out.println();
        System.out.println("-- " + text);
    }

    public static void out(String text) {
        System.out.println("   " + text);
    }

    public static void out(String label, Object value) {
        System.out.println("   " + label + " = " + value);
    }

    // ------------------------------------------------------------------
    // shared fixtures
    // ------------------------------------------------------------------

    /**
     * Inserts the users every query example works with.
     *
     * @return the inserted users, in declaration order
     */
    public static List<User> seedUsers(DataStore db) {
        return db.saveAll(List.of(
                new User("Alice", 25, "ACTIVE"),
                new User("Bob", 17, "ACTIVE"),
                new User("Charlie", 30, "INACTIVE"),
                new User("Dave", 15, "INACTIVE"),
                new User("Eve", 22, "ACTIVE")));
    }
}
