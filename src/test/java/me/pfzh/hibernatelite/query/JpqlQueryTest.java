package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.pagination.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpqlQueryTest {

    private JpqlExecutor executor;
    private JpqlQuery<Object> query;

    @BeforeEach
    void setUp() {
        executor = org.mockito.Mockito.mock(JpqlExecutor.class);
        query = new JpqlQuery<>("SELECT u FROM User u", Object.class, executor);
    }

    // ==================== 构造器 ====================

    @Test
    void constructor_nullJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new JpqlQuery<>(null, Object.class, executor));
    }

    @Test
    void constructor_blankJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new JpqlQuery<>("  ", Object.class, executor));
    }

    @Test
    void constructor_nullResultType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new JpqlQuery<>("SELECT u FROM User u", null, executor));
    }

    @Test
    void constructor_nullExecutor_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new JpqlQuery<>("SELECT u FROM User u", Object.class, null));
    }

    // ==================== 参数绑定 ====================

    @Test
    void param_returnsSameInstance() {
        assertSame(query, query.param("name", "Alice"));
    }

    @Test
    void param_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () -> query.param(null, "x"));
    }

    @Test
    void param_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> query.param("  ", "x"));
    }

    // ==================== 委托 ====================

    @Test
    void list_delegatesToExecutor() {
        java.util.List<Object> expected = java.util.List.of(new Object());
        org.mockito.Mockito.when(executor.list(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.<Class<Object>>any(),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(expected);

        assertSame(expected, query.list());
    }

    @Test
    void one_delegatesToExecutor() {
        Object expected = new Object();
        org.mockito.Mockito.when(executor.one(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.<Class<Object>>any(),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(expected);

        assertSame(expected, query.one());
    }

    @Test
    void count_delegatesToExecutor() {
        org.mockito.Mockito.when(executor.count(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(42L);

        assertEquals(42L, query.count());
    }

    @Test
    void exists_delegatesToExecutor() {
        org.mockito.Mockito.when(executor.exists(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.<Class<Object>>any(),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(true);

        assertTrue(query.exists());
    }

    // ==================== page ====================

    @Test
    void page_withoutCountJpql_throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> query.page(PageRequest.of(0, 10)));
    }

    @Test
    void page_withCountJpql_delegatesToExecutor() {
        PageRequest request = PageRequest.of(0, 10);
        me.pfzh.hibernatelite.pagination.Page<Object> expected =
                me.pfzh.hibernatelite.pagination.Page.of(java.util.List.of(), request, 0L);

        org.mockito.Mockito.when(executor.page(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.<Class<Object>>any(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        assertSame(expected, query.page("SELECT COUNT(u) FROM User u", request));
    }
}