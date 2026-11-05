package me.pfzh.hibernatelite.examples;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.examples.model.Purchase;
import me.pfzh.hibernatelite.examples.model.User;
import org.hibernate.LazyInitializationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Category: bootstrap, lifecycle, batching and detached state.
 *
 * <p>Covers every {@code Builder} option, try-with-resources, idempotent
 * {@code close()}, use-after-close, the 50-row batch boundary and the fact that
 * every entity is detached as soon as a call returns.</p>
 */
public final class LifecycleExample {

    public static void main(String[] args) {
        ExampleSupport.run("9. BOOTSTRAP, LIFECYCLE AND DETACHED STATE",
                LifecycleExample::demo);
    }

    static void demo(DataStore db) {

        // ---------------------------------------------------------------
        ExampleSupport.step("Builder: every option");
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:lifecycle-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        try (DataStore custom = HibernateLite.builder()
                .dataSource(ds)                 // required
                .entities(User.class)           // required, at least one
                .showSql(false)                 // optional, default false
                .ddlAuto("create-drop")         // optional, default "validate"
                .build()) {
            custom.save(new User("InCustomStore", 20, "ACTIVE"));
            ExampleSupport.out("custom store count", custom.query(User.class).count());
        }
        ds.close();

        // ---------------------------------------------------------------
        ExampleSupport.step("Builder validation");
        try {
            HibernateLite.builder().build();
        } catch (IllegalStateException e) {
            ExampleSupport.out("build() without a DataSource -> IllegalStateException");
        }
        try {
            HibernateLite.builder().entities();
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("entities() without arguments -> IllegalArgumentException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("close(): idempotent, and the store is unusable afterwards");
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setJdbcUrl("jdbc:h2:mem:lifecycle2-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds2.setUsername("sa");
        ds2.setPassword("");
        ds2.setDriverClassName("org.h2.Driver");
        DataStore closing = HibernateLite.builder()
                .dataSource(ds2).entities(User.class).ddlAuto("create-drop").build();
        closing.close();
        closing.close();
        ExampleSupport.out("close() twice: no exception", true);
        try {
            closing.find(User.class, 1L);
        } catch (RuntimeException e) {
            ExampleSupport.out("use after close -> " + e.getClass().getSimpleName());
        }
        ds2.close();

        // ---------------------------------------------------------------
        ExampleSupport.step("saveAll(): the 50-row batch boundary");
        List<User> many = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            many.add(new User("Bulk" + i, i, "ACTIVE"));
        }
        List<User> saved = db.saveAll(many);
        ExampleSupport.out("returned size", saved.size());
        ExampleSupport.out("all ids assigned", saved.stream().allMatch(u -> u.getId() != null));
        ExampleSupport.out("ids distinct", saved.stream().map(User::getId).distinct().count());
        ExampleSupport.out("stored rows", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("every returned entity is detached");
        User authored = db.save(new User("Author", 40, "ACTIVE"));
        User reference = new User();
        reference.setId(authored.getId());          // only the id: forces a proxy
        Purchase purchase = db.save(new Purchase("Book", reference));

        Purchase detached = db.find(Purchase.class, purchase.getId());
        try {
            detached.getUser().getName();
        } catch (LazyInitializationException e) {
            ExampleSupport.out("lazy association outside a transaction -> LazyInitializationException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("inside a transaction the same navigation works");
        String name = db.transaction(() -> {
            Purchase inside = db.find(Purchase.class, purchase.getId());
            return inside.getUser().getName();
        });
        ExampleSupport.out("author name inside transaction", name);

        // ---------------------------------------------------------------
        ExampleSupport.step("unwrap() still returns the factory after close()");
        HikariDataSource ds3 = new HikariDataSource();
        ds3.setJdbcUrl("jdbc:h2:mem:lifecycle3-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds3.setUsername("sa");
        ds3.setPassword("");
        ds3.setDriverClassName("org.h2.Driver");
        DataStore local = HibernateLite.builder()
                .dataSource(ds3).entities(User.class).ddlAuto("create-drop").build();
        local.close();
        ExampleSupport.out("unwrap(...).isClosed()",
                local.unwrap(org.hibernate.SessionFactory.class).isClosed());
        ds3.close();
    }
}
