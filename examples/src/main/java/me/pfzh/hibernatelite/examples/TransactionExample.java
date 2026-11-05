package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.User;

/**
 * Category: transactions.
 *
 * <p>Covers the callback form, the Runnable form, rollback on failure, REQUIRED
 * nesting, rollback-only propagation, and cleanup after an {@code Error}.</p>
 */
public final class TransactionExample {

    public static void main(String[] args) {
        ExampleSupport.run("7. TRANSACTIONS", TransactionExample::demo);
    }

    static void demo(DataStore db) {

        // ---------------------------------------------------------------
        ExampleSupport.step("transaction(callback): commit on normal return");
        Long id = db.transaction(() -> {
            User u = db.save(new User("Alice", 30, "ACTIVE"));
            return u.getId();
        });
        ExampleSupport.out("returned id", id);
        ExampleSupport.out("visible after commit", db.find(User.class, id).getName());

        // ---------------------------------------------------------------
        ExampleSupport.step("transaction(Runnable): no return value");
        db.transaction(() -> {
            db.save(new User("Bob", 17, "ACTIVE"));
        });
        ExampleSupport.out("count", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("rollback on RuntimeException");
        try {
            db.transaction(() -> {
                db.save(new User("RolledBack", 20, "ACTIVE"));
                throw new IllegalStateException("something went wrong");
            });
        } catch (IllegalStateException e) {
            ExampleSupport.out("exception propagated unchanged: " + e.getMessage());
        }
        ExampleSupport.out("count (RolledBack is absent)", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("nested transaction: only the outermost commits");
        db.transaction(() -> {
            db.save(new User("Outer1", 20, "ACTIVE"));
            db.transaction(() -> {
                db.save(new User("Inner1", 21, "ACTIVE"));
                db.transaction(() -> {
                    db.save(new User("Inner2", 22, "ACTIVE"));
                });
            });
            db.save(new User("Outer2", 23, "ACTIVE"));
            return null;
        });
        ExampleSupport.out("count after nesting", db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("nested failure rolls back everything");
        long before = db.query(User.class).count();
        try {
            db.transaction(() -> {
                db.save(new User("N1", 20, "ACTIVE"));
                db.transaction(() -> {
                    db.save(new User("N2", 21, "ACTIVE"));
                    throw new IllegalStateException("inner boom");
                });
                return null;
            });
        } catch (IllegalStateException e) {
            ExampleSupport.out("propagated", e.getMessage());
        }
        ExampleSupport.out("count unchanged", db.query(User.class).count() == before);

        // ---------------------------------------------------------------
        ExampleSupport.step("swallowed inner failure poisons the outer transaction");
        long beforePoison = db.query(User.class).count();
        try {
            db.transaction(() -> {
                db.save(new User("P1", 20, "ACTIVE"));
                try {
                    db.transaction(() -> {
                        db.save(new User("P2", 21, "ACTIVE"));
                        throw new IllegalStateException("inner boom");
                    });
                } catch (IllegalStateException ignored) {
                    ExampleSupport.out("user swallowed the inner exception", true);
                }
                db.save(new User("P3", 22, "ACTIVE"));
                return null;
            });
        } catch (HibernateLiteException e) {
            ExampleSupport.out("outer commit -> HibernateLiteException");
            ExampleSupport.out("message", e.getMessage());
        }
        ExampleSupport.out("count unchanged", db.query(User.class).count() == beforePoison);

        // ---------------------------------------------------------------
        ExampleSupport.step("an Error also rolls back and leaves the thread usable");
        long beforeError = db.query(User.class).count();
        try {
            db.transaction(() -> {
                db.save(new User("ErrorRow", 20, "ACTIVE"));
                throw new AssertionError("simulated fatal error");
            });
        } catch (AssertionError e) {
            ExampleSupport.out("AssertionError propagated: " + e.getMessage());
        }
        ExampleSupport.out("count unchanged", db.query(User.class).count() == beforeError);

        db.save(new User("AfterError", 20, "ACTIVE"));
        ExampleSupport.out("the same thread can still be used, count",
                db.query(User.class).count());

        // ---------------------------------------------------------------
        ExampleSupport.step("automatic transactions: a single save is atomic by itself");
        User auto = db.save(new User("Auto", 20, "ACTIVE"));
        ExampleSupport.out("saved without an explicit transaction", auto.getId() != null);
    }
}
