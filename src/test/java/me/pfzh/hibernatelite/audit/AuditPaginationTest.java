package me.pfzh.hibernatelite.audit;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit of the pagination module.
 *
 * <p>Expectations are taken from {@code README.md} section "5. Pagination"
 * and from the Javadoc of {@link Page} / {@link PageRequest}.</p>
 */
class AuditPaginationTest {

    private DataStore db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        ds = AuditSupport.newDataSource("audit-page");
        db = AuditSupport.newDataStore(ds, TestUser.class);
        List<TestUser> users = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            users.add(new TestUser("U" + i, i));
        }
        db.saveAll(users);
    }

    @AfterEach
    void tearDown() {
        AuditSupport.quietClose(db, ds);
    }

    // ---------- PageRequest contract ----------

    @Test
    void offset_isPageTimesSize() {
        assertEquals(0, PageRequest.of(0, 20).offset());
        assertEquals(20, PageRequest.of(1, 20).offset());
        assertEquals(40, PageRequest.of(2, 20).offset());
    }

    @Test
    void offset_largePage_mustNotOverflowToNegative() {
        // A negative offset would be passed to Query#setFirstResult(int).
        // Correct behaviour: reject the request instead of overflowing.
        assertThrows(IllegalArgumentException.class,
                () -> PageRequest.of(Integer.MAX_VALUE, 1000));

        // Large but representable offsets must still work.
        assertEquals(2_000_000_000, PageRequest.of(2_000_000, 1000).offset());
    }

    @Test
    void pageRequest_rejects_invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, -5));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 1001));
        assertEquals(1000, PageRequest.of(0, 1000).size());
    }

    @Test
    void withoutCount_returnsNewImmutableInstance() {
        PageRequest base = PageRequest.of(3, 7);
        PageRequest slice = base.withoutCount();
        assertNotSame(base, slice);
        assertTrue(base.countTotal());
        assertFalse(slice.countTotal());
        assertEquals(3, slice.page());
        assertEquals(7, slice.size());
    }

    // ---------- total-count mode ----------

    @Test
    void totalCountMode_reportsTotals() {
        Page<TestUser> p = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 4));

        assertEquals(4, p.numberOfElements());
        assertEquals(10, p.totalElements());
        assertEquals(3, p.totalPages());
        assertTrue(p.hasNext());
        assertFalse(p.hasPrevious());
        assertTrue(p.isFirst());
        assertFalse(p.isLast());
        assertTrue(p.hasTotal());
        assertEquals(0, p.page());
        assertEquals(4, p.size());
    }

    @Test
    void totalCountMode_lastPartialPage() {
        Page<TestUser> p = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(2, 4));

        assertEquals(2, p.numberOfElements());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
        assertTrue(p.hasPrevious());
    }

    @Test
    void totalCountMode_pageFarBeyondEnd_isEmptyAndLast() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(50, 4));

        assertTrue(p.isEmpty());
        assertEquals(0, p.numberOfElements());
        assertEquals(10, p.totalElements());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
        assertTrue(p.hasPrevious());
    }

    @Test
    void totalCountMode_emptyResult_totalPagesIsZero() {
        Page<TestUser> p = db.query(TestUser.class)
                .eq(TestUser::getName, "does-not-exist")
                .page(PageRequest.of(0, 4));

        assertTrue(p.isEmpty());
        assertEquals(0, p.totalElements());
        assertEquals(0, p.totalPages());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
    }

    // ---------- slice mode ----------

    @Test
    void sliceMode_exactMultipleOfPageSize_hasNextAccurate() {
        // 10 rows, size 5 -> page 0 must report hasNext=true, page 1 false.
        Page<TestUser> first = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 5).withoutCount());
        assertTrue(first.hasNext(), "page 0 of exactly 2 full pages must have a next page");
        assertFalse(first.hasTotal());
        assertThrows(IllegalStateException.class, first::totalElements);
        assertThrows(IllegalStateException.class, first::totalPages);

        Page<TestUser> second = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(1, 5).withoutCount());
        assertEquals(5, second.numberOfElements());
        assertFalse(second.hasNext());
        assertTrue(second.isLast());
    }

    @Test
    void sliceMode_pageSizeEqualsRowCount_hasNextFalse() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 10).withoutCount());
        assertEquals(10, p.numberOfElements());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
    }

    @Test
    void sliceMode_maxAllowedPageSize_stillWorks() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 1000).withoutCount());
        assertEquals(10, p.numberOfElements());
        assertFalse(p.hasNext());
    }

    @Test
    void sliceMode_emptyResult() {
        Page<TestUser> p = db.query(TestUser.class)
                .eq(TestUser::getName, "nope")
                .page(PageRequest.of(0, 5).withoutCount());
        assertTrue(p.isEmpty());
        assertFalse(p.hasNext());
        assertTrue(p.isLast());
        assertTrue(p.isFirst());
    }

    // ---------- Page value-object contract ----------

    @Test
    void content_isUnmodifiable() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 3));
        assertThrows(UnsupportedOperationException.class,
                () -> p.content().add(new TestUser("x")));
    }

    @Test
    void content_isDefensiveCopy() {
        List<TestUser> mutable = new ArrayList<>(List.of(new TestUser("a")));
        Page<TestUser> p = Page.of(mutable, PageRequest.of(0, 5), 1);
        mutable.clear();
        assertEquals(1, p.numberOfElements(), "Page must defensively copy its content");
    }

    @Test
    void map_preservesPaginationMetadata() {
        Page<TestUser> p = db.query(TestUser.class)
                .orderByAsc(TestUser::getAge)
                .page(PageRequest.of(0, 4));
        Page<String> names = p.map(TestUser::getName);

        assertEquals(4, names.numberOfElements());
        assertEquals(p.page(), names.page());
        assertEquals(p.size(), names.size());
        assertEquals(p.totalElements(), names.totalElements());
        assertEquals(p.hasNext(), names.hasNext());
        assertEquals(p.hasTotal(), names.hasTotal());
        assertEquals(p.isLast(), names.isLast());
    }

    @Test
    void map_preservesSliceMetadata() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 4).withoutCount());
        Page<Integer> ages = p.map(TestUser::getAge);
        assertFalse(ages.hasTotal());
        assertEquals(p.hasNext(), ages.hasNext());
    }

    @Test
    void page_orderingIsStableAcrossPages() {
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Page<TestUser> p = db.query(TestUser.class)
                    .orderByAsc(TestUser::getAge)
                    .page(PageRequest.of(i, 2));
            p.content().forEach(u -> seen.add(u.getAge()));
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), seen);
    }

    @Test
    void page_withConditions_totalCountRespectsConditions() {
        Page<TestUser> p = db.query(TestUser.class)
                .gt(TestUser::getAge, 5)
                .page(PageRequest.of(0, 3));
        assertEquals(5, p.totalElements());
        assertEquals(2, p.totalPages());
        assertEquals(3, p.numberOfElements());
    }

    @Test
    void page_withSpecOnly_totalCountRespectsSpec() {
        Page<TestUser> p = db.query(TestUser.class)
                .where((cb, root) -> cb.gt(root.get("age"), 8))
                .page(PageRequest.of(0, 3));
        assertEquals(2, p.totalElements());
    }

    @Test
    void page_negativePageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).page(PageRequest.of(-1, 3)));
    }

    @Test
    void page_nullRequestRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> db.query(TestUser.class).page(null));
    }

    @Test
    void page_ofPageBeyondRange_doesNotThrow() {
        assertDoesNotThrow(() -> db.query(TestUser.class).page(PageRequest.of(999, 10)));
    }

    @Test
    void page_updateDtoMappingWithDuplicatesIsFine() {
        Page<TestUser> p = db.query(TestUser.class).page(PageRequest.of(0, 5));
        Page<Map<String, Object>> mapped = p.map(u -> Map.of("name", u.getName()));
        assertEquals(5, mapped.numberOfElements());
    }
}
