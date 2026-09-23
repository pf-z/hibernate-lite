package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.pagination.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Verifies the chainable API of {@link LambdaQuery}.
 *
 * <p>Does not touch a database. The {@link QueryExecutor} is mocked
 * to verify delegation only.</p>
 */
class LambdaQueryTest {

    static class User {
        public String getName() { return null; }
        public Integer getAge() { return null; }
    }

    private QueryExecutor executor;
    private LambdaQuery<User> query;

    @BeforeEach
    void setUp() {
        executor = mock(QueryExecutor.class);
        query = new LambdaQuery<>(User.class, executor);
    }

    // ==================== 构造器 ====================

    @Test
    void constructor_nullClass_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LambdaQuery<>(null, executor));
    }

    @Test
    void constructor_nullExecutor_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LambdaQuery<>(User.class, null));
    }

    // ==================== 链式返回 ====================

    @Test
    void conditions_returnSameInstance() {
        assertSame(query, query.eq(User::getName, "a"));
        assertSame(query, query.ne(User::getName, "b"));
        assertSame(query, query.gt(User::getAge, 1));
        assertSame(query, query.ge(User::getAge, 1));
        assertSame(query, query.lt(User::getAge, 1));
        assertSame(query, query.le(User::getAge, 1));
        assertSame(query, query.like(User::getName, "a%"));
        assertSame(query, query.in(User::getName, List.of("a")));
        assertSame(query, query.notIn(User::getName, List.of("a")));
        assertSame(query, query.isNull(User::getName));
        assertSame(query, query.isNotNull(User::getName));
        assertSame(query, query.between(User::getAge, 1, 10));
    }

    @Test
    void orderBy_returnSameInstance() {
        assertSame(query, query.orderByAsc(User::getName));
        assertSame(query, query.orderByDesc(User::getAge));
    }

    @Test
    void where_returnsSameInstance() {
        assertSame(query, query.where((cb, root) -> null));
    }

    // ==================== 参数校验 ====================

    @Test
    void where_nullSpec_throws() {
        assertThrows(IllegalArgumentException.class, () -> query.where(null));
    }

    // ==================== 委托到 executor：查询 ====================

    @Test
    void list_delegatesToExecutor() {
        List<User> expected = List.of(new User());
        when(executor.list(eq(User.class), anyList(), anyList(), anyList()))
                .thenReturn(expected);

        List<User> result = query.list();

        assertSame(expected, result);
        verify(executor).list(eq(User.class), anyList(), anyList(), anyList());
    }

    @Test
    void listWithLimit_delegatesToExecutor() {
        List<User> expected = List.of(new User());
        when(executor.list(eq(User.class), anyList(), anyList(), eq(10), anyList()))
                .thenReturn(expected);

        List<User> result = query.list(10);

        assertSame(expected, result);
        verify(executor).list(eq(User.class), anyList(), anyList(), eq(10), anyList());
    }

    @Test
    void one_delegatesToExecutor() {
        User expected = new User();
        when(executor.one(eq(User.class), anyList(), anyList(), anyList()))
                .thenReturn(expected);

        assertSame(expected, query.one());
    }

    @Test
    void count_delegatesToExecutor() {
        when(executor.count(eq(User.class), anyList(), anyList())).thenReturn(42L);

        assertEquals(42L, query.count());
    }

    @Test
    void exists_delegatesToExecutor() {
        when(executor.exists(eq(User.class), anyList(), anyList())).thenReturn(true);

        assertTrue(query.exists());
    }

    @Test
    void delete_delegatesToExecutor() {
        when(executor.delete(eq(User.class), anyList(), anyList())).thenReturn(3);

        assertEquals(3, query.delete());
    }

    @Test
    void page_delegatesToExecutor() {
        PageRequest request = PageRequest.of(0, 10);
        var expected = me.pfzh.hibernatelite.pagination.Page.of(
                List.of(new User()), request, 1);
        when(executor.page(eq(User.class), anyList(), anyList(), eq(request), anyList()))
                .thenReturn(expected);

        assertSame(expected, query.page(request));
    }

    @Test
    void page_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class, () -> query.page(null));
    }

    // ==================== 委托到 executor：更新 ====================

    @Test
    void update_singleField_delegatesToExecutor() {
        when(executor.update(eq(User.class), anyList(), anyList(), anyMap()))
                .thenReturn(3);

        int result = query.update(User::getName, "updated");

        assertEquals(3, result);
        verify(executor).update(eq(User.class), anyList(), anyList(), anyMap());
    }

    @Test
    void update_map_delegatesToExecutor() {
        when(executor.update(eq(User.class), anyList(), anyList(), anyMap()))
                .thenReturn(5);

        int result = query.update(Map.of("name", "x"));

        assertEquals(5, result);
        verify(executor).update(eq(User.class), anyList(), anyList(), anyMap());
    }
}