package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.User;
import me.pfzh.hibernatelite.internal.SessionContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * Category: escape hatches.
 *
 * <p>Covers {@code where(QuerySpec)} with raw JPA Criteria, and
 * {@code unwrap(SessionFactory.class)} for native SQL and anything the facade
 * does not expose.</p>
 */
public final class EscapeHatchExample {

    public static void main(String[] args) {
        ExampleSupport.run("8. ESCAPE HATCHES", EscapeHatchExample::demo);
    }

    static void demo(DataStore db) {
        ExampleSupport.seedUsers(db);
        SessionFactory sf = db.unwrap(SessionFactory.class);

        // ---------------------------------------------------------------
        ExampleSupport.step("where(spec): a raw Criteria predicate");
        List<User> users = db.query(User.class)
                .where((cb, root) -> cb.or(
                        cb.like(root.get("name"), "A%"),
                        cb.like(root.get("name"), "B%")))
                .list();
        ExampleSupport.out("result", users.stream().map(User::getName).toList());

        // ---------------------------------------------------------------
        ExampleSupport.step("where(spec) combined with the DSL by AND");
        List<User> mixed = db.query(User.class)
                .eq(User::getStatus, "ACTIVE")
                .where((cb, root) -> cb.gt(root.get("age"), 24))
                .list();
        ExampleSupport.out("result", mixed.stream().map(User::getName).toList());

        // ---------------------------------------------------------------
        ExampleSupport.step("several where() calls are AND-ed");
        List<User> multi = db.query(User.class)
                .where((cb, root) -> cb.gt(root.get("age"), 20))
                .where((cb, root) -> cb.lt(root.get("age"), 24))
                .list();
        ExampleSupport.out("result", multi.stream().map(User::getName).toList());

        // ---------------------------------------------------------------
        ExampleSupport.step("a spec returning null adds no restriction");
        ExampleSupport.out("count", db.query(User.class)
                .where((cb, root) -> null).list().size());

        // ---------------------------------------------------------------
        ExampleSupport.step("unwrap(SessionFactory.class): native SQL");
        try (Session session = sf.openSession()) {
            session.beginTransaction();
            Long total = session
                    .createNativeQuery("select count(*) from ex_users", Long.class)
                    .getSingleResult();
            ExampleSupport.out("native count", total);

            List<Object[]> rows = session
                    .createNativeQuery("select name, age from ex_users order by age", Object[].class)
                    .list();
            rows.forEach(r -> ExampleSupport.out("native row", r[0] + " / " + r[1]));

            session.getTransaction().commit();
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("unwrap: unsupported type and null");
        try {
            db.unwrap(String.class);
        } catch (HibernateLiteException caught) {
            ExampleSupport.out("unwrap(String.class) -> HibernateLiteException");
        }
        try {
            db.unwrap(null);
        } catch (IllegalArgumentException caught) {
            ExampleSupport.out("unwrap(null) -> IllegalArgumentException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("SessionContext.current(sf): reuse the library's own Session");
        db.transaction(() -> {
            Session session = SessionContext.current(sf);
            Long total = session
                    .createQuery("select count(u) from User u", Long.class)
                    .getSingleResult();
            ExampleSupport.out("count from the current Session", total);
            ExampleSupport.out("same Session is reused", session == SessionContext.current(sf));
            return null;
        });
    }
}
