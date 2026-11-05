package me.pfzh.hibernatelite.examples;

import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.examples.model.User;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

/**
 * Category: pagination.
 *
 * <p>Covers the whole {@link PageRequest} and {@link Page} surface, in both
 * total-count mode and slice mode.</p>
 */
public final class PaginationExample {

    public static void main(String[] args) {
        ExampleSupport.run("4. PAGINATION", PaginationExample::demo);
    }

    static void demo(DataStore db) {
        for (int i = 1; i <= 10; i++) {
            db.save(new User("U" + i, i, i % 2 == 0 ? "EVEN" : "ODD"));
        }

        // ---------------------------------------------------------------
        ExampleSupport.step("PageRequest: construction and accessors");
        PageRequest request = PageRequest.of(1, 3);
        ExampleSupport.out("page()", request.page());
        ExampleSupport.out("size()", request.size());
        ExampleSupport.out("offset()", request.offset());
        ExampleSupport.out("countTotal()", request.countTotal());
        ExampleSupport.out("firstPage(3).offset()", PageRequest.firstPage(3).offset());
        ExampleSupport.out("of(1,3).withoutCount().countTotal()",
                PageRequest.of(1, 3).withoutCount().countTotal());

        assertEquals(PageRequest.of(1, 3).offset(), 3, "offset = page * size");
        ExampleSupport.out("PageRequest.of(1,3) is immutable, withoutCount() returns a new one",
                request != request.withoutCount());

        // ---------------------------------------------------------------
        ExampleSupport.step("total-count mode: page 0 of 4");
        Page<User> first = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(0, 4));

        ExampleSupport.out("content()", names(first));
        ExampleSupport.out("numberOfElements()", first.numberOfElements());
        ExampleSupport.out("isEmpty()", first.isEmpty());
        ExampleSupport.out("page()", first.page());
        ExampleSupport.out("size()", first.size());
        ExampleSupport.out("hasTotal()", first.hasTotal());
        ExampleSupport.out("totalElements()", first.totalElements());
        ExampleSupport.out("totalPages()", first.totalPages());
        ExampleSupport.out("hasNext()", first.hasNext());
        ExampleSupport.out("hasPrevious()", first.hasPrevious());
        ExampleSupport.out("isFirst()", first.isFirst());
        ExampleSupport.out("isLast()", first.isLast());

        // ---------------------------------------------------------------
        ExampleSupport.step("total-count mode: last (partial) page");
        Page<User> last = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(2, 4));
        ExampleSupport.out("content()", names(last));
        ExampleSupport.out("hasNext()", last.hasNext());
        ExampleSupport.out("isLast()", last.isLast());

        // ---------------------------------------------------------------
        ExampleSupport.step("total-count mode: a page beyond the end");
        Page<User> beyond = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(99, 4));
        ExampleSupport.out("isEmpty()", beyond.isEmpty());
        ExampleSupport.out("totalElements()", beyond.totalElements());
        ExampleSupport.out("hasNext()", beyond.hasNext());
        ExampleSupport.out("hasPrevious()", beyond.hasPrevious());

        // ---------------------------------------------------------------
        ExampleSupport.step("slice mode: no COUNT statement, hasNext still exact");
        Page<User> slice = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(0, 4).withoutCount());
        ExampleSupport.out("content()", names(slice));
        ExampleSupport.out("hasTotal()", slice.hasTotal());
        ExampleSupport.out("hasNext()", slice.hasNext());
        try {
            slice.totalElements();
        } catch (IllegalStateException e) {
            ExampleSupport.out("totalElements() in slice mode -> IllegalStateException");
        }
        try {
            slice.totalPages();
        } catch (IllegalStateException e) {
            ExampleSupport.out("totalPages() in slice mode -> IllegalStateException");
        }

        // exactly two full pages: page 0 must still report a next page
        Page<User> exact = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(0, 5).withoutCount());
        ExampleSupport.out("size=5, 10 rows -> hasNext()", exact.hasNext());

        // ---------------------------------------------------------------
        ExampleSupport.step("DTO mapping preserves pagination metadata");
        Page<String> names = db.query(User.class)
                .orderByAsc(User::getAge)
                .page(PageRequest.of(0, 4))
                .map(User::getName);
        ExampleSupport.out("mapped content()", names.content());
        ExampleSupport.out("mapped totalElements()", names.totalElements());
        ExampleSupport.out("mapped totalPages()", names.totalPages());
        ExampleSupport.out("mapped page()/size()", names.page() + "/" + names.size());

        // ---------------------------------------------------------------
        ExampleSupport.step("pagination combined with conditions");
        Page<User> active = db.query(User.class)
                .eq(User::getStatus, "EVEN")
                .orderByAsc(User::getAge)
                .page(PageRequest.of(0, 2));
        ExampleSupport.out("content()", names(active));
        ExampleSupport.out("totalElements()", active.totalElements());

        // ---------------------------------------------------------------
        ExampleSupport.step("invalid requests are rejected");
        try {
            PageRequest.of(-1, 10);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("page < 0 -> IllegalArgumentException: " + e.getMessage());
        }
        try {
            PageRequest.of(0, 1001);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("size > 1000 -> IllegalArgumentException: " + e.getMessage());
        }
        try {
            PageRequest.of(Integer.MAX_VALUE, 1000);
        } catch (IllegalArgumentException e) {
            ExampleSupport.out("page * size overflow -> IllegalArgumentException: " + e.getMessage());
        }
    }

    private static java.util.List<String> names(Page<User> page) {
        return page.content().stream().map(User::getName).toList();
    }

    private static void assertEquals(int actual, int expected, String what) {
        if (actual != expected) {
            throw new IllegalStateException(what + ": expected " + expected + " but was " + actual);
        }
    }
}
