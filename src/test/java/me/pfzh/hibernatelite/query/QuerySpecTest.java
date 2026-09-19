package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuerySpecTest {

    @Test
    void lambdaCanBeAssigned() {
        // 验证 @FunctionalInterface 可被 lambda 赋值
        QuerySpec<Object> spec = (CriteriaBuilder cb, Root<Object> root) -> null;
        assertNotNull(spec);
    }

    @Test
    void nullPredicateIsAllowed() {
        QuerySpec<Object> spec = (cb, root) -> null;
        assertNull(spec.toPredicate(null, null));
    }
}