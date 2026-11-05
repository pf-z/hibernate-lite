package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.User;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Category: conditional UPDATE and DELETE.
 *
 * <p>Covers both update overloads, conditional delete, the safety guards, and
 * the effect of bulk DML on the persistence context.</p>
 */
public final class BulkOperationExample {

    public static void main(String[] args) {
        ExampleSupport.run("5. BULK UPDATE / DELETE", BulkOperationExample::demo);
    }

    static void demo(DataStore db) {
        ExampleSupport.seedUsers(db);

        // ---------------------------------------------------------------
        ExampleSupport.step("update(field, value): one SQL statement");
        int updated = db.query(User.class)
                .eq(User::getStatus, "ACTIVE")
                .update(User::getStatus, "VERIFIED");
        ExampleSupport.out("rows updated", updated);
        ExampleSupport.out("remaining ACTIVE", db.query(User.class)
                .eq(User::getStatus, "ACTIVE").count());

        // ---------------------------------------------------------------
        ExampleSupport.step("update(Map): several columns at once");
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("age", 26);
        changes.put("status", "VIP");
        int updatedByName = db.query(User.class)
                .eq(User::getName, "Alice")
                .update(changes);
        ExampleSupport.out("rows updated", updatedByName);
        ExampleSupport.out("Alice now", db.query(User.class)
                .eq(User::getName, "Alice").one().getAge() + " / "
                + db.query(User.class).eq(User::getName, "Alice").one().getStatus());

        // ---------------------------------------------------------------
        ExampleSupport.step("update(): no match returns 0");
        ExampleSupport.out("rows updated", db.query(User.class)
                .eq(User::getName, "Nobody").update(User::getStatus, "X"));

        // ---------------------------------------------------------------
        ExampleSupport.step("delete(): conditional");
        int deleted = db.query(User.class).lt(User::getAge, 18).delete();
        ExampleSupport.out("rows deleted", deleted);
        ExampleSupport.out("remaining", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("safety guard: unconditional delete is refused");
        try {
            db.query(User.class).delete();
        } catch (HibernateLiteException e) {
            ExampleSupport.out("delete() -> HibernateLiteException");
            ExampleSupport.out("message", e.getMessage());
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("safety guard: unconditional update is refused");
        try {
            db.query(User.class).update(User::getStatus, "PWNED");
        } catch (HibernateLiteException e) {
            ExampleSupport.out("update() -> HibernateLiteException");
            ExampleSupport.out("message", e.getMessage());
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("safety guard: a spec that contributes nothing is also refused");
        try {
            db.query(User.class).where((cb, root) -> null).delete();
        } catch (HibernateLiteException e) {
            ExampleSupport.out("where(spec -> null).delete() -> HibernateLiteException");
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("guard passes when a real condition exists");
        int afterSpec = db.query(User.class)
                .where((cb, root) -> cb.gt(root.get("age"), 1000))
                .delete();
        ExampleSupport.out("rows deleted by an always-false spec", afterSpec);

        // ---------------------------------------------------------------
        ExampleSupport.step("bulk DML clears the persistence context");
        db.transaction(() -> {
            User loaded = db.query(User.class).eq(User::getName, "Charlie").one();
            ExampleSupport.out("before bulk update", loaded.getStatus());

            db.query(User.class)
                    .eq(User::getName, "Charlie")
                    .update(User::getStatus, "CHANGED");

            // The previous instance is detached now, so reload it.
            User reloaded = db.find(User.class, loaded.getId());
            ExampleSupport.out("after bulk update (reloaded)", reloaded.getStatus());
            return null;
        });

        // ---------------------------------------------------------------
        ExampleSupport.step("explicit 'match everything' is allowed");
        int all = db.query(User.class)
                .where((cb, root) -> cb.conjunction())
                .update(User::getStatus, "RESET");
        ExampleSupport.out("rows updated with cb.conjunction()", all);
    }
}
