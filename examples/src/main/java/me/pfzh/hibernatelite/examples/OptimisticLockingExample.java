package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.examples.model.VersionedAccount;

/**
 * Category: optimistic locking.
 *
 * <p>Shows that {@code save()} and {@code delete()} reject a stale detached
 * snapshot when the entity has a {@code @Version} field, and that the failure
 * is reported as the library's own exception type.</p>
 */
public final class OptimisticLockingExample {

    public static void main(String[] args) {
        ExampleSupport.run("11. OPTIMISTIC LOCKING", OptimisticLockingExample::demo);
    }

    static void demo(DataStore db) {
        VersionedAccount account = db.save(new VersionedAccount("alice", 100));
        Long id = account.getId();

        // ------------------------------------------------------------------
        ExampleSupport.step("two detached snapshots; the first writer wins");
        VersionedAccount first = db.find(VersionedAccount.class, id);
        VersionedAccount stale = db.find(VersionedAccount.class, id);

        first.setBalance(120);
        db.save(first);
        ExampleSupport.out("balance after first save",
                db.find(VersionedAccount.class, id));

        stale.setBalance(90);
        try {
            db.save(stale);
            ExampleSupport.out("stale save", "unexpectedly succeeded");
        } catch (HibernateLiteException e) {
            ExampleSupport.out("stale save", e.getClass().getSimpleName());
            ExampleSupport.out("cause", e.getCause() == null
                    ? "none"
                    : e.getCause().getClass().getName());
        }

        // ------------------------------------------------------------------
        ExampleSupport.step("delete through a stale snapshot is rejected too");
        VersionedAccount staleForDelete = db.find(VersionedAccount.class, id);
        VersionedAccount current = db.find(VersionedAccount.class, id);
        current.setBalance(150);
        db.save(current);

        try {
            db.delete(staleForDelete);
            ExampleSupport.out("stale delete", "unexpectedly succeeded");
        } catch (HibernateLiteException e) {
            ExampleSupport.out("stale delete", e.getMessage());
        }

        ExampleSupport.out("row after rejected delete",
                db.find(VersionedAccount.class, id));

        // ------------------------------------------------------------------
        ExampleSupport.step("scope: bulk update/delete do not perform a per-row version check");
        ExampleSupport.out("note", "use save()/delete() per entity when @Version matters");
    }
}
