package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Type-safe query DSL.
 *
 * <p>Created via {@code db.query(EntityClass.class)}. Chainable:
 * every configuration method returns {@code this}, and terminal
 * methods ({@code list}, {@code one}, ...) execute the query.</p>
 *
 * <p><b>Not thread-safe.</b> Each query should be built and used
 * within a single thread.</p>
 *
 * @param <T> entity type
 *
 */
public final class LambdaQuery<T> {

    private final Class<T> entityClass;
    private final QueryExecutor executor;
    private final List<QueryCondition> conditions = new ArrayList<>();
    private final List<QueryOrder> orders = new ArrayList<>();
    private final List<QuerySpec<T>> specs = new ArrayList<>();

    /**
     * <b>Internal API.</b> Created by {@code DataStore.query(...)}.
     */
    public LambdaQuery(Class<T> entityClass, QueryExecutor executor) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass cannot be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        this.entityClass = entityClass;
        this.executor = executor;
    }

    // ==================== 条件 ====================

    public LambdaQuery<T> eq(SFunction<T, ?> field, Object value) {
        return add(field, Operator.EQ, value);
    }

    public LambdaQuery<T> ne(SFunction<T, ?> field, Object value) {
        return add(field, Operator.NE, value);
    }

    public LambdaQuery<T> gt(SFunction<T, ?> field, Object value) {
        return add(field, Operator.GT, value);
    }

    public LambdaQuery<T> ge(SFunction<T, ?> field, Object value) {
        return add(field, Operator.GE, value);
    }

    public LambdaQuery<T> lt(SFunction<T, ?> field, Object value) {
        return add(field, Operator.LT, value);
    }

    public LambdaQuery<T> le(SFunction<T, ?> field, Object value) {
        return add(field, Operator.LE, value);
    }

    public LambdaQuery<T> like(SFunction<T, ?> field, String value) {
        return add(field, Operator.LIKE, value);
    }

    public LambdaQuery<T> in(SFunction<T, ?> field, Collection<?> values) {
        return add(field, Operator.IN, values);
    }

    public LambdaQuery<T> notIn(SFunction<T, ?> field, Collection<?> values) {
        return add(field, Operator.NOT_IN, values);
    }

    public LambdaQuery<T> isNull(SFunction<T, ?> field) {
        return add(field, Operator.IS_NULL, null);
    }

    public LambdaQuery<T> isNotNull(SFunction<T, ?> field) {
        return add(field, Operator.IS_NOT_NULL, null);
    }

    public LambdaQuery<T> between(SFunction<T, ?> field, Object low, Object high) {
        return add(field, Operator.BETWEEN, new Object[]{low, high});
    }

    // ==================== 排序 ====================

    public LambdaQuery<T> orderByAsc(SFunction<T, ?> field) {
        orders.add(new QueryOrder(field, true));
        return this;
    }

    public LambdaQuery<T> orderByDesc(SFunction<T, ?> field) {
        orders.add(new QueryOrder(field, false));
        return this;
    }

    // ==================== 逃生舱 ====================

    public LambdaQuery<T> where(QuerySpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        specs.add(spec);
        return this;
    }

    // ==================== 执行 ====================

    public List<T> list() {
        return executor.list(entityClass, conditions, orders, specs);
    }

    public List<T> list(int limit) {
        return executor.list(entityClass, conditions, orders, limit, specs);
    }

    public T one() {
        return executor.one(entityClass, conditions, orders, specs);
    }

    public long count() {
        return executor.count(entityClass, conditions, specs);
    }

    public boolean exists() {
        return executor.exists(entityClass, conditions, specs);
    }

    public int delete() {
        return executor.delete(entityClass, conditions, specs);
    }

    public Page<T> page(PageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        return executor.page(entityClass, conditions, orders, request, specs);
    }

    // ==================== 内部 ====================

    private LambdaQuery<T> add(SFunction<T, ?> field, Operator op, Object value) {
        conditions.add(new QueryCondition(field, op, value));
        return this;
    }
}