package me.pfzh.hibernatelite.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryOrderTest {

    static class User {
        public String getName() { return null; }
        public Integer getAge() { return null; }
    }

    @Test
    void ascending() {
        QueryOrder o = new QueryOrder(User::getName, true);
        assertEquals("name", o.fieldName());
        assertTrue(o.ascending());
    }

    @Test
    void descending() {
        QueryOrder o = new QueryOrder(User::getAge, false);
        assertEquals("age", o.fieldName());
        assertFalse(o.ascending());
    }

    @Test
    void nullField_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryOrder(null, true));
    }
}