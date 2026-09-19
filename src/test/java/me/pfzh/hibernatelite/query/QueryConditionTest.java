package me.pfzh.hibernatelite.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryConditionTest {

    static class User {
        public String getName() { return null; }
        public Integer getAge() { return null; }
    }

    // ==================== 正常构造 ====================

    @Test
    void eq_valid() {
        QueryCondition c = new QueryCondition(User::getName, Operator.EQ, "alice");
        assertEquals("name", c.fieldName());
        assertEquals(Operator.EQ, c.operator());
        assertEquals("alice", c.value());
    }

    @Test
    void ne_valid() {
        QueryCondition c = new QueryCondition(User::getName, Operator.NE, "alice");
        assertEquals(Operator.NE, c.operator());
    }

    @Test
    void gt_valid() {
        QueryCondition c = new QueryCondition(User::getAge, Operator.GT, 18);
        assertEquals("age", c.fieldName());
    }

    @Test
    void isNull_noValue() {
        QueryCondition c = new QueryCondition(User::getName, Operator.IS_NULL, null);
        assertEquals(Operator.IS_NULL, c.operator());
        assertNull(c.value());
    }

    @Test
    void isNotNull_noValue() {
        QueryCondition c = new QueryCondition(User::getName, Operator.IS_NOT_NULL, null);
        assertEquals(Operator.IS_NOT_NULL, c.operator());
    }

    @Test
    void like_validString() {
        QueryCondition c = new QueryCondition(User::getName, Operator.LIKE, "Al%");
        assertEquals("Al%", c.value());
    }

    @Test
    void in_validIterable() {
        QueryCondition c = new QueryCondition(User::getName, Operator.IN,
                List.of("a", "b"));
        assertEquals(List.of("a", "b"), c.value());
    }

    @Test
    void notIn_validIterable() {
        QueryCondition c = new QueryCondition(User::getName, Operator.NOT_IN,
                List.of("a", "b"));
        assertEquals(Operator.NOT_IN, c.operator());
    }

    @Test
    void between_validArray() {
        QueryCondition c = new QueryCondition(User::getAge, Operator.BETWEEN,
                new Object[]{18, 65});
        assertArrayEquals(new Object[]{18, 65}, (Object[]) c.value());
    }

    // ==================== 构造校验 ====================

    @Test
    void nullField_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(null, Operator.EQ, "x"));
    }

    @Test
    void nullOperator_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getName, null, "x"));
    }

    @Test
    void eq_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getName, Operator.EQ, null));
    }

    @Test
    void gt_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getAge, Operator.GT, null));
    }

    @Test
    void like_nonString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getName, Operator.LIKE, 123));
    }

    @Test
    void like_null_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getName, Operator.LIKE, null));
    }

    @Test
    void in_nonIterable_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getName, Operator.IN, "not iterable"));
    }

    @Test
    void between_wrongLength_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getAge, Operator.BETWEEN,
                        new Object[]{18}));
    }

    @Test
    void between_nonArray_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryCondition(User::getAge, Operator.BETWEEN, "not array"));
    }
}