package me.pfzh.hibernatelite.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {

    // ==================== 正常构造 ====================

    @Test
    void of_validArgs() {
        PageRequest r = PageRequest.of(0, 20);
        assertEquals(0, r.page());
        assertEquals(20, r.size());
        assertEquals(0, r.offset());
        assertTrue(r.countTotal());
    }

    @Test
    void of_secondPage_offset() {
        PageRequest r = PageRequest.of(2, 10);
        assertEquals(2, r.page());
        assertEquals(10, r.size());
        assertEquals(20, r.offset());
    }

    @Test
    void of_largePage() {
        PageRequest r = PageRequest.of(1000, 100);
        assertEquals(1000, r.page());
        assertEquals(100_000, r.offset());
    }

    @Test
    void firstPage() {
        PageRequest r = PageRequest.firstPage(50);
        assertEquals(0, r.page());
        assertEquals(50, r.size());
        assertEquals(0, r.offset());
        assertTrue(r.countTotal());
    }

    // ==================== withoutCount ====================

    @Test
    void withoutCount_disablesTotal() {
        PageRequest r = PageRequest.of(1, 20).withoutCount();
        assertEquals(1, r.page());
        assertEquals(20, r.size());
        assertFalse(r.countTotal());
    }

    @Test
    void withoutCount_preservesOffset() {
        PageRequest r = PageRequest.of(3, 10).withoutCount();
        assertEquals(30, r.offset());
    }

    @Test
    void withoutCount_isImmutable() {
        PageRequest original = PageRequest.of(0, 20);
        PageRequest modified = original.withoutCount();

        assertTrue(original.countTotal());
        assertFalse(modified.countTotal());
        assertNotSame(original, modified);
    }

    // ==================== 校验 ====================

    @Test
    void negativePage_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PageRequest.of(-1, 20));
        assertTrue(ex.getMessage().contains("page must be >= 0"));
    }

    @Test
    void zeroSize_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PageRequest.of(0, 0));
        assertTrue(ex.getMessage().contains("size must be"));
    }

    @Test
    void negativeSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PageRequest.of(0, -5));
    }

    @Test
    void sizeOverMax_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PageRequest.of(0, 1001));
        assertTrue(ex.getMessage().contains("size must be"));
    }

    @Test
    void sizeAtMax_ok() {
        PageRequest r = PageRequest.of(0, 1000);
        assertEquals(1000, r.size());
    }
}