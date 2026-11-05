package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of the Lambda query DSL.
 *
 * <p>Expectations are taken from {@code README.md} section "3. Query DSL".</p>
 */
class AuditQueryTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-query");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        db.saveAll(List.of(
                new TestUser("Alice", 25, "ACTIVE"),
                new TestUser("Bob", 17, "ACTIVE"),
                new TestUser("Charlie", 30, "INACTIVE"),
                new TestUser("Dave", 15, "INACTIVE"),
                new TestUser("Eve", 22, "ACTIVE")
        ));
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- validation of arguments ----------

    @Test
    void queryNullTypeRejected() {
        assertThrows(RuntimeException.class, () -> db.query((Class<Object>) null));
    }

    @Test
    void eqWithNullValueRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).eq(TestUser::getName, null));
        assertTrue(e.getMessage().contains("isNull"), "should point users to isNull(): " + e.getMessage());
    }

    @Test
    void neWithNullValueRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).ne(TestUser::getName, null));
    }

    @Test
    void likeWithNonStringRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).like(TestUser::getAge, null));
    }

    @Test
    void inWithNonIterableRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).in(TestUser::getName, (java.util.Collection<?>) null));
    }

    @Test
    void compareWithNonComparableValue_failsClearly() {
        // age is Integer; passing a String must not silently produce wrong SQL.
        assertThrows(RuntimeException.class,
                () -> db.query(TestUser.class).gt(TestUser::getAge, "eighteen").list());
    }

    // ---------- operators ----------

    @Test
    void allComparisonOperatorsAgreeWithJavaFiltering() {
        List<TestUser> all = db.query(TestUser.class).orderByAsc(TestUser::getAge).list();

        assertEquals(ages(filter(all, u -> u.getAge() > 18)),
                ages(db.query(TestUser.class).gt(TestUser::getAge, 18).orderByAsc(TestUser::getAge).list()));
        assertEquals(ages(filter(all, u -> u.getAge() >= 22)),
                ages(db.query(TestUser.class).ge(TestUser::getAge, 22).orderByAsc(TestUser::getAge).list()));
        assertEquals(ages(filter(all, u -> u.getAge() < 22)),
                ages(db.query(TestUser.class).lt(TestUser::getAge, 22).orderByAsc(TestUser::getAge).list()));
        assertEquals(ages(filter(all, u -> u.getAge() <= 17)),
                ages(db.query(TestUser.class).le(TestUser::getAge, 17).orderByAsc(TestUser::getAge).list()));
        assertEquals(ages(filter(all, u -> !u.getName().equals("Alice"))),
                ages(db.query(TestUser.class).ne(TestUser::getName, "Alice").orderByAsc(TestUser::getAge).list()));
    }

    private static List<TestUser> filter(List<TestUser> in, java.util.function.Predicate<TestUser> p) {
        return in.stream().filter(p).toList();
    }

    private static List<Integer> ages(List<TestUser> users) {
        return users.stream().map(TestUser::getAge).toList();
    }

    @Test
    void inEmptyCollection_failsAtBuildTime() {
        assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).in(TestUser::getName, List.of()).list());
    }

    @Test
    void notInEmptyCollection_failsAtBuildTime() {
        assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).notIn(TestUser::getName, List.of()).list());
    }

    @Test
    void betweenInvertedBounds_returnsEmpty() {
        List<TestUser> users = db.query(TestUser.class).between(TestUser::getAge, 30, 15).list();
        assertTrue(users.isEmpty());
    }

    @Test
    void betweenInclusiveBounds() {
        List<TestUser> users = db.query(TestUser.class)
                .between(TestUser::getAge, 17, 25)
                .orderByAsc(TestUser::getAge)
                .list();
        assertEquals(List.of(17, 22, 25),
                users.stream().map(TestUser::getAge).toList());
    }

    @Test
    void likeUnderscoreAndPercentWork() {
        assertEquals(1, db.query(TestUser.class).like(TestUser::getName, "A_ice").list().size());
        assertEquals(5, db.query(TestUser.class).like(TestUser::getName, "%").list().size());
        assertEquals(0, db.query(TestUser.class).like(TestUser::getName, "a%").list().size(),
                "LIKE must be case sensitive on H2 with default collation");
    }

    @Test
    void isNullAndIsNotNull() {
        db.save(new TestUser("NoAge", null));
        assertEquals(1, db.query(TestUser.class).isNull(TestUser::getAge).list().size());
        assertEquals(5, db.query(TestUser.class).isNotNull(TestUser::getAge).list().size());
    }

    @Test
    void likeOnNumericField_castsToText() {
        // Observed behaviour: Hibernate silently casts the numeric column to
        // text, so LIKE on a non-String field neither fails nor warns.
        assertEquals(2, db.query(TestUser.class).like(TestUser::getAge, "2%").list().size());
    }

    // ---------- one / count / exists ----------

    @Test
    void oneWithMultipleMatches_throws() {
        // 3 users share status ACTIVE
        HibernateLiteException e = assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).eq(TestUser::getStatus, "ACTIVE").one());
        assertTrue(e.getMessage().contains("more than one"));
    }

    @Test
    void oneWithNoMatch_returnsNull() {
        assertNull(db.query(TestUser.class).eq(TestUser::getName, "zzz").one());
    }

    @Test
    void oneWithExactlyOneMatch() {
        TestUser u = db.query(TestUser.class).eq(TestUser::getName, "Alice").one();
        assertNotNull(u);
        assertEquals(25, u.getAge());
    }

    @Test
    void countAndExistsAgree() {
        assertEquals(5, db.query(TestUser.class).count());
        assertEquals(3, db.query(TestUser.class).eq(TestUser::getStatus, "ACTIVE").count());
        assertTrue(db.query(TestUser.class).eq(TestUser::getStatus, "ACTIVE").exists());
        assertFalse(db.query(TestUser.class).eq(TestUser::getStatus, "NOPE").exists());
        assertEquals(0, db.query(TestUser.class).eq(TestUser::getStatus, "NOPE").count());
    }

    // ---------- ordering ----------

    @Test
    void multipleOrderBy_isAppliedInDeclarationOrder() {
        db.saveAll(List.of(
                new TestUser("Zoe", 25, "ACTIVE"),
                new TestUser("Amy", 25, "ACTIVE")));

        List<TestUser> users = db.query(TestUser.class)
                .eq(TestUser::getAge, 25)
                .orderByAsc(TestUser::getAge)
                .orderByAsc(TestUser::getName)
                .list();

        assertEquals(List.of("Alice", "Amy", "Zoe"),
                users.stream().map(TestUser::getName).toList());
    }

    @Test
    void orderByWithNullValues() {
        db.save(new TestUser("NullAge", null));
        // Must not throw; null ordering is database dependent, just require stability.
        assertDoesNotThrow(() -> db.query(TestUser.class).orderByAsc(TestUser::getAge).list());
        assertEquals(6, db.query(TestUser.class).orderByAsc(TestUser::getAge).list().size());
    }

    // ---------- list limits ----------

    @Test
    void listWithZeroLimitRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).list(0));
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).list(-1));
    }

    @Test
    void defaultListLimitSilentlyTruncatesAt1000() {
        List<TestUser> many = new ArrayList<>();
        for (int i = 0; i < 1005; i++) many.add(new TestUser("bulk-" + i));
        db.saveAll(many);

        // Documented behaviour ("default limit 1000"), but silently hiding 5 rows
        // is a sharp edge: count() reports 1010 while list() returns 1000.
        assertEquals(1000, db.query(TestUser.class).list().size());
        assertEquals(1010, db.query(TestUser.class).count());
    }

    // ---------- bulk delete ----------

    @Test
    void deleteWithoutConditionRejected() {
        HibernateLiteException e = assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).delete());
        assertTrue(e.getMessage().contains("refusing to delete all rows"));
        assertEquals(5, db.query(TestUser.class).count());
    }

    @Test
    void deleteWithCondition() {
        assertEquals(2, db.query(TestUser.class).lt(TestUser::getAge, 18).delete());
        assertEquals(3, db.query(TestUser.class).count());
    }

    @Test
    void deleteIsIdempotentOnSecondCall() {
        assertEquals(2, db.query(TestUser.class).lt(TestUser::getAge, 18).delete());
        assertEquals(0, db.query(TestUser.class).lt(TestUser::getAge, 18).delete());
    }

    // ---------- bulk update ----------

    @Test
    void updateWithoutConditionRejected() {
        HibernateLiteException e = assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class).update(TestUser::getStatus, "X"));
        assertTrue(e.getMessage().contains("refusing to update all rows"));
        assertEquals(5, db.query(TestUser.class).count());
    }

    @Test
    void updateSingleField() {
        assertEquals(3, db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .update(TestUser::getStatus, "VERIFIED"));
        assertEquals(3, db.query(TestUser.class).eq(TestUser::getStatus, "VERIFIED").count());
    }

    @Test
    void updateMultipleFields() {
        assertEquals(1, db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .update(Map.of("age", 26, "status", "VIP")));
        TestUser u = db.query(TestUser.class).eq(TestUser::getName, "Alice").one();
        assertEquals(26, u.getAge());
        assertEquals("VIP", u.getStatus());
    }

    @Test
    void updateToNull() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", null);
        assertEquals(1, db.query(TestUser.class)
                .eq(TestUser::getName, "Alice")
                .update(updates));
        assertNull(db.find(TestUser.class,
                db.query(TestUser.class).eq(TestUser::getName, "Alice").one().getId()).getStatus());
    }

    @Test
    void updateUnknownFieldRejected() {
        assertThrows(HibernateLiteException.class,
                () -> db.query(TestUser.class)
                        .eq(TestUser::getName, "Alice")
                        .update(Map.of("nmae", "typo")));
    }

    @Test
    void updateWithEmptyMapRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class)
                        .eq(TestUser::getName, "Alice")
                        .update(Map.of()));
    }

    /**
     * Classic JPA bulk-update pitfall that the library does not document nor hide.
     * The persistence context is not cleared after a bulk UPDATE, so a previously
     * loaded entity stays stale for the remainder of the transaction.
     */
    @Test
    void bulkUpdate_doesNotInvalidateFirstLevelCache_insideTransaction() {
        Long id = db.query(TestUser.class).eq(TestUser::getName, "Alice").one().getId();

        db.transaction(() -> {
            TestUser managed = db.find(TestUser.class, id);
            assertEquals("ACTIVE", managed.getStatus());

            int updated = db.query(TestUser.class)
                    .eq(TestUser::getName, "Alice")
                    .update(TestUser::getStatus, "CHANGED");
            assertEquals(1, updated);

            TestUser reread = db.find(TestUser.class, id);
            return null;
        });

        // Outside the transaction the value is correct; the stale read is only
        // observable inside the transaction.
        assertEquals("CHANGED", db.find(TestUser.class, id).getStatus());
    }

    /**
     * Same pitfall for bulk DELETE: an entity deleted by a bulk statement stays
     * in the first-level cache and is handed back by find().
     */
    @Test
    void bulkDelete_thenFind_insideSameTransaction() {
        Long id = db.query(TestUser.class).eq(TestUser::getName, "Alice").one().getId();

        db.transaction(() -> {
            TestUser managed = db.find(TestUser.class, id);
            assertNotNull(managed);

            db.query(TestUser.class).eq(TestUser::getName, "Alice").delete();

            TestUser afterDelete = db.find(TestUser.class, id);
            // A deleted row must not be returned as if it still existed.
            assertNull(afterDelete,
                    "find() returned a row that was already bulk-deleted (stale first-level cache)");
            return null;
        });
    }

    // ---------- escape hatch ----------

    @Test
    void whereSpecReturningNull_meansNoRestriction() {
        assertEquals(5, db.query(TestUser.class).where((cb, root) -> null).list().size());
    }

    @Test
    void whereSpecCombinedWithDsl() {
        assertEquals(1, db.query(TestUser.class)
                .eq(TestUser::getStatus, "ACTIVE")
                .where((cb, root) -> cb.gt(root.get("age"), 24))
                .list().size());
    }

    @Test
    void multipleWhereSpecsAreAnded() {
        assertEquals(1, db.query(TestUser.class)
                .where((cb, root) -> cb.gt(root.get("age"), 20))
                .where((cb, root) -> cb.lt(root.get("age"), 24))
                .list().size());   // only Eve (22)
    }

    @Test
    void whereSpecExceptionIsPropagated() {
        assertThrows(IllegalStateException.class,
                () -> db.query(TestUser.class)
                        .where((cb, root) -> {
                            throw new IllegalStateException("spec boom");
                        })
                        .list());
    }

    @Test
    void queryOnNonEntityClass_reportsProblem() {
        assertThrows(RuntimeException.class, () -> db.query(String.class).list());
    }
}
