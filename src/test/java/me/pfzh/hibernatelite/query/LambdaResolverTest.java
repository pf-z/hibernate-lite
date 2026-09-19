package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LambdaResolverTest {

    static class User {
        public String getName() { return null; }
        public boolean isActive() { return false; }
        public Integer getAge() { return null; }
        public String getURL() { return null; }
        public String getX() { return null; }
        public String nonGetter() { return null; }
    }

    // ==================== 正常 getter ====================

    @Test
    void resolve_getName() {
        assertEquals("name", LambdaResolver.resolve(User::getName));
    }

    @Test
    void resolve_isActive() {
        assertEquals("active", LambdaResolver.resolve(User::isActive));
    }

    @Test
    void resolve_getAge() {
        assertEquals("age", LambdaResolver.resolve(User::getAge));
    }

    // ==================== 大小写边界 ====================

    @Test
    void resolve_getURL_keepsUppercase() {
        assertEquals("URL", LambdaResolver.resolve(User::getURL));
    }

    @Test
    void resolve_getX_singleChar() {
        assertEquals("x", LambdaResolver.resolve(User::getX));
    }

    // ==================== null 检查 ====================

    @Test
    void resolve_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> LambdaResolver.resolve((SFunction<User, String>) null));
    }

    // ==================== 非 getter 拒绝 ====================

    @Test
    void resolve_nonGetter_throws() {
        assertThrows(HibernateLiteException.class,
                () -> LambdaResolver.resolve(User::nonGetter));
    }
}