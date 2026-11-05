package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.Account;
import me.pfzh.hibernatelite.examples.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Category: CRUD.
 *
 * <p>Covers {@code find}, {@code save} (insert and update), {@code saveAll},
 * {@code delete} in all three shapes, and the business-key restriction.</p>
 */
public final class CrudExample {

    public static void main(String[] args) {
        ExampleSupport.run("2. CRUD", CrudExample::demo);
    }

    static void demo(DataStore db) {

        // ---------------------------------------------------------------
        ExampleSupport.step("save(): INSERT when the identifier is null");
        User alice = new User("Alice", 30, "ACTIVE");
        User saved = db.save(alice);
        ExampleSupport.out("generated id", saved.getId());
        ExampleSupport.out("same instance as input (persist)", saved == alice);

        // ---------------------------------------------------------------
        ExampleSupport.step("save(): UPDATE when the identifier is set");
        saved.setName("Alice Smith");
        User updated = db.save(saved);
        ExampleSupport.out("id unchanged", updated.getId());
        ExampleSupport.out("same instance as input (merge)", updated == saved);
        ExampleSupport.out("reloaded name", db.find(User.class, updated.getId()).getName());

        // ---------------------------------------------------------------
        ExampleSupport.step("find(): hit, miss and null arguments");
        ExampleSupport.out("find existing", db.find(User.class, updated.getId()));
        ExampleSupport.out("find missing ", db.find(User.class, 999_999L));
        try {
            db.find(null, 1L);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("find(null, id) -> IllegalArgumentException: " + e.getMessage());
        }
        try {
            db.find(User.class, null);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("find(type, null) -> IllegalArgumentException: " + e.getMessage());
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("saveAll(): one transaction, input order preserved");
        List<User> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(new User("Batch" + i, 20 + i, "ACTIVE"));
        }
        List<User> inserted = db.saveAll(batch);
        ExampleSupport.out("size", inserted.size());
        ExampleSupport.out("order preserved", inserted.get(0).getName() + " .. "
                + inserted.get(inserted.size() - 1).getName());
        ExampleSupport.out("all have ids",
                inserted.stream().allMatch(u -> u.getId() != null));

        ExampleSupport.out("saveAll(emptyList).size()", db.saveAll(List.of()).size());

        // ---------------------------------------------------------------
        ExampleSupport.step("delete(): managed entity");
        Long aliceId = updated.getId();
        db.delete(updated);
        ExampleSupport.out("find after delete", db.find(User.class, aliceId));

        // ---------------------------------------------------------------
        ExampleSupport.step("delete(): detached entity (id only)");
        User detached = new User();
        detached.setId(inserted.get(0).getId());
        db.delete(detached);
        ExampleSupport.out("row count after delete", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("delete(): idempotent, missing row is not an error");
        db.delete(detached);                       // already gone
        User neverExisted = new User();
        neverExisted.setId(424_242L);
        db.delete(neverExisted);
        ExampleSupport.out("no exception was thrown", true);

        // ---------------------------------------------------------------
        ExampleSupport.step("save(): business-key entity is rejected");
        Account account = new Account("alice", 100);
        try {
            db.save(account);
        } catch (HibernateLiteException e) {
            ExampleSupport.out("save(Account) -> HibernateLiteException");
            ExampleSupport.out("message", e.getMessage());
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("business-key entities are inserted via the escape hatch");
        db.transaction(() -> {
            var session = me.pfzh.hibernatelite.internal.SessionContext.current(
                    db.unwrap(org.hibernate.SessionFactory.class));
            session.persist(new Account("alice", 100));
        });
        ExampleSupport.out("find(Account.class, \"alice\")",
                db.find(Account.class, "alice"));
    }
}
