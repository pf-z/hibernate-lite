package me.pfzh.hibernatelite.pagination;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    // ==================== of() ====================

    @Test
    void of_basicMetadata() {
        Page<String> page = Page.of(List.of("a", "b"), PageRequest.of(0, 10), 100);

        assertEquals(2, page.numberOfElements());
        assertEquals(0, page.page());
        assertEquals(10, page.size());
        assertEquals(100, page.totalElements());
        assertEquals(10, page.totalPages());
        assertTrue(page.hasTotal());
    }

    @Test
    void of_nullContent_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Page.of(null, PageRequest.of(0, 10), 0));
        assertTrue(ex.getMessage().contains("content cannot be null"));
    }

    @Test
    void of_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Page.of(List.of(), null, 0));
    }

    @Test
    void of_negativeTotal_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Page.of(List.of(), PageRequest.of(0, 10), -1));
    }

    // ==================== 内容 ====================

    @Test
    void content_returned() {
        List<String> items = List.of("a", "b", "c");
        Page<String> page = Page.of(items, PageRequest.of(0, 10), 3);

        assertEquals(items, page.content());
        assertEquals(3, page.numberOfElements());
        assertFalse(page.isEmpty());
    }

    @Test
    void content_isImmutable() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        Page<String> page = Page.of(mutable, PageRequest.of(0, 10), 2);

        // External mutation does not affect the page
        mutable.add("c");
        assertEquals(2, page.content().size());

        // Returned content cannot be modified
        assertThrows(UnsupportedOperationException.class,
                () -> page.content().add("d"));
    }

    @Test
    void emptyContent() {
        Page<String> page = Page.of(List.of(), PageRequest.of(0, 10), 0);

        assertTrue(page.isEmpty());
        assertEquals(0, page.numberOfElements());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
        assertFalse(page.hasNext());
        assertTrue(page.isFirst());
        assertTrue(page.isLast());
    }

    // ==================== 导航（有 total） ====================

    @Test
    void hasNext_trueWhenNotLast() {
        Page<String> page = Page.of(List.of("a"), PageRequest.of(0, 10), 100);

        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void hasNext_falseOnLastPage() {
        Page<String> page = Page.of(List.of("a"), PageRequest.of(9, 10), 100);

        assertFalse(page.hasNext());
        assertTrue(page.hasPrevious());
        assertFalse(page.isFirst());
        assertTrue(page.isLast());
    }

    @Test
    void hasNext_middlePage() {
        Page<String> page = Page.of(List.of("a"), PageRequest.of(5, 10), 100);

        assertTrue(page.hasNext());
        assertTrue(page.hasPrevious());
        assertFalse(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void totalPages_roundsUp() {
        Page<String> page = Page.of(List.of(), PageRequest.of(0, 10), 25);
        assertEquals(3, page.totalPages());
    }

    @Test
    void totalPages_exactDivision() {
        Page<String> page = Page.of(List.of(), PageRequest.of(0, 10), 30);
        assertEquals(3, page.totalPages());
    }

    // ==================== slice()：无总数 ====================

    @Test
    void slice_hasTotalFalse() {
        Page<String> page = Page.slice(
                List.of("a", "b"), PageRequest.of(0, 10).withoutCount(), false);

        assertFalse(page.hasTotal());
        assertEquals(0, page.page());
        assertEquals(10, page.size());
        assertEquals(2, page.numberOfElements());
    }

    @Test
    void slice_totalElements_throws() {
        Page<String> page = Page.slice(
                List.of("a"), PageRequest.of(0, 10).withoutCount(), false);

        assertThrows(IllegalStateException.class, page::totalElements);
    }

    @Test
    void slice_totalPages_throws() {
        Page<String> page = Page.slice(
                List.of("a"), PageRequest.of(0, 10).withoutCount(), false);

        assertThrows(IllegalStateException.class, page::totalPages);
    }

    @Test
    void slice_hasNextTrue() {
        Page<String> page = Page.slice(
                List.of("a", "b"), PageRequest.of(0, 2).withoutCount(), true);

        assertTrue(page.hasNext());
        assertFalse(page.isLast());
    }

    @Test
    void slice_hasNextFalse() {
        Page<String> page = Page.slice(
                List.of("a"), PageRequest.of(0, 2).withoutCount(), false);

        assertFalse(page.hasNext());
        assertTrue(page.isLast());
    }

    @Test
    void slice_hasNextTrueButLastPage() {
        // hasNext 字段决定，与 content.size() 无关
        Page<String> page = Page.slice(
                List.of("a"), PageRequest.of(0, 10).withoutCount(), true);

        assertTrue(page.hasNext());
        assertFalse(page.isLast());
    }

    @Test
    void slice_nullContent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Page.slice(null, PageRequest.of(0, 10), false));
    }

    @Test
    void slice_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Page.slice(List.of(), null, false));
    }

    @Test
    void slice_contentIsImmutable() {
        List<String> mutable = new ArrayList<>(List.of("a"));
        Page<String> page = Page.slice(
                mutable, PageRequest.of(0, 10).withoutCount(), false);

        mutable.add("b");
        assertEquals(1, page.content().size());
        assertThrows(UnsupportedOperationException.class,
                () -> page.content().add("c"));
    }

    // ==================== map() ====================

    @Test
    void map_transformsContent() {
        Page<Integer> ints = Page.of(List.of(1, 2, 3), PageRequest.of(0, 10), 100);
        Page<String> strings = ints.map(i -> "n" + i);

        assertEquals(List.of("n1", "n2", "n3"), strings.content());
        assertEquals(0, strings.page());
        assertEquals(10, strings.size());
        assertEquals(100, strings.totalElements());
        assertTrue(strings.hasTotal());
    }

    @Test
    void map_slice_preservesHasTotal() {
        Page<Integer> ints = Page.slice(
                List.of(1, 2), PageRequest.of(0, 10).withoutCount(), false);
        Page<String> strings = ints.map(i -> "n" + i);

        assertFalse(strings.hasTotal());
        assertThrows(IllegalStateException.class, strings::totalElements);
    }

    @Test
    void map_slice_preservesHasNext() {
        Page<Integer> ints = Page.slice(
                List.of(1, 2), PageRequest.of(0, 10).withoutCount(), true);
        Page<String> strings = ints.map(i -> "n" + i);

        assertTrue(strings.hasNext());
        assertFalse(strings.isLast());
    }

    @Test
    void map_nullMapper_throws() {
        Page<Integer> ints = Page.of(List.of(1), PageRequest.of(0, 10), 1);
        assertThrows(IllegalArgumentException.class, () -> ints.map(null));
    }

    @Test
    void map_emptyPage() {
        Page<Integer> ints = Page.of(List.of(), PageRequest.of(0, 10), 0);
        Page<String> strings = ints.map(i -> "n" + i);

        assertTrue(strings.isEmpty());
        assertEquals(0, strings.totalElements());
    }

    @Test
    void map_preservesNavigationState() {
        Page<Integer> ints = Page.of(List.of(1), PageRequest.of(5, 10), 100);
        Page<String> strings = ints.map(i -> "n" + i);

        assertEquals(5, strings.page());
        assertTrue(strings.hasNext());
        assertTrue(strings.hasPrevious());
        assertFalse(strings.isFirst());
        assertFalse(strings.isLast());
    }
}