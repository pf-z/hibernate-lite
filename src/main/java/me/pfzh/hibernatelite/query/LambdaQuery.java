package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Type-safe fluent query DSL for building and executing entity operations.
 *
 * <p>A {@code LambdaQuery} is created through
 * {@code db.query(EntityClass.class)}. Query conditions, ordering rules,
 * and custom {@link QuerySpec} predicates are collected first and executed
 * only when a terminal operation such as {@link #list()}, {@link #one()},
 * or {@link #delete()} is called.</p>
 *
 * <p>This class is a query API facade rather than the actual query executor.
 * The collected query definition is delegated to {@link QueryExecutor},
 * which translates it into JPA Criteria queries and executes them through
 * Hibernate.</p>
 *
 * <p><b>Not thread-safe.</b> A query instance is mutable while it is being
 * built, so each query should be created, configured, and executed within
 * a single thread.</p>
 *
 * @param <T> entity type
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
public final class LambdaQuery<T> {

    /** Entity type targeted by this query. */
    private final Class<T> entityClass;

    /**
     * Executes the query definition.
     *
     * <p>The executor is kept separate so that this DSL remains focused
     * on building the query rather than managing Hibernate sessions,
     * transactions, or Criteria API details.</p>
     */
    private final QueryExecutor executor;

    /** Conditions added through the type-safe DSL. */
    private final List<QueryCondition> conditions = new ArrayList<>();

    /** Ordering rules applied to result queries. */
    private final List<QueryOrder> orders = new ArrayList<>();

    /**
     * Custom predicates supplied through the escape-hatch API.
     *
     * <p>These predicates are combined with the DSL conditions using
     * logical AND.</p>
     */
    private final List<QuerySpec<T>> specs = new ArrayList<>();

    /**
     * Creates a query for the specified entity type.
     *
     * <p>This is primarily an internal API and is normally called by
     * {@code DataStore.query(...)}.</p>
     *
     * @param entityClass entity type to query
     * @param executor query executor
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

    // ==================== Conditions ====================

    /**
     * Adds an equality condition.
     *
     * @param field entity field
     * @param value expected value
     * @return this query
     */
    public LambdaQuery<T> eq(SFunction<T, ?> field, Object value) {
        return add(field, Operator.EQ, value);
    }

    /**
     * Adds a not-equal condition.
     *
     * @param field entity field
     * @param value value to exclude
     * @return this query
     */
    public LambdaQuery<T> ne(SFunction<T, ?> field, Object value) {
        return add(field, Operator.NE, value);
    }

    /**
     * Adds a greater-than condition.
     *
     * @param field entity field
     * @param value lower bound
     * @return this query
     */
    public LambdaQuery<T> gt(SFunction<T, ?> field, Object value) {
        return add(field, Operator.GT, value);
    }

    /**
     * Adds a greater-than-or-equal condition.
     *
     * @param field entity field
     * @param value lower bound
     * @return this query
     */
    public LambdaQuery<T> ge(SFunction<T, ?> field, Object value) {
        return add(field, Operator.GE, value);
    }

    /**
     * Adds a less-than condition.
     *
     * @param field entity field
     * @param value upper bound
     * @return this query
     */
    public LambdaQuery<T> lt(SFunction<T, ?> field, Object value) {
        return add(field, Operator.LT, value);
    }

    /**
     * Adds a less-than-or-equal condition.
     *
     * @param field entity field
     * @param value upper bound
     * @return this query
     */
    public LambdaQuery<T> le(SFunction<T, ?> field, Object value) {
        return add(field, Operator.LE, value);
    }

    /**
     * Adds a SQL {@code LIKE} condition.
     *
     * @param field entity field
     * @param value pattern to match
     * @return this query
     */
    public LambdaQuery<T> like(SFunction<T, ?> field, String value) {
        return add(field, Operator.LIKE, value);
    }

    /**
     * Adds an {@code IN} condition.
     *
     * @param field entity field
     * @param values values to match
     * @return this query
     */
    public LambdaQuery<T> in(SFunction<T, ?> field, Collection<?> values) {
        return add(field, Operator.IN, values);
    }

    /**
     * Adds a {@code NOT IN} condition.
     *
     * @param field entity field
     * @param values values to exclude
     * @return this query
     */
    public LambdaQuery<T> notIn(SFunction<T, ?> field, Collection<?> values) {
        return add(field, Operator.NOT_IN, values);
    }

    /**
     * Adds an {@code IS NULL} condition.
     *
     * @param field entity field
     * @return this query
     */
    public LambdaQuery<T> isNull(SFunction<T, ?> field) {
        return add(field, Operator.IS_NULL, null);
    }

    /**
     * Adds an {@code IS NOT NULL} condition.
     *
     * @param field entity field
     * @return this query
     */
    public LambdaQuery<T> isNotNull(SFunction<T, ?> field) {
        return add(field, Operator.IS_NOT_NULL, null);
    }

    /**
     * Adds a {@code BETWEEN} condition.
     *
     * @param field entity field
     * @param low lower bound
     * @param high upper bound
     * @return this query
     */
    public LambdaQuery<T> between(SFunction<T, ?> field, Object low, Object high) {
        return add(field, Operator.BETWEEN, new Object[]{low, high});
    }

    // ==================== Ordering ====================

    /**
     * Adds ascending ordering by the specified field.
     *
     * <p>Multiple ordering rules are applied in the order in which
     * they are added.</p>
     *
     * @param field entity field
     * @return this query
     */
    public LambdaQuery<T> orderByAsc(SFunction<T, ?> field) {
        orders.add(new QueryOrder(field, true));
        return this;
    }

    /**
     * Adds descending ordering by the specified field.
     *
     * @param field entity field
     * @return this query
     */
    public LambdaQuery<T> orderByDesc(SFunction<T, ?> field) {
        orders.add(new QueryOrder(field, false));
        return this;
    }

    // ==================== Escape hatch ====================

    /**
     * Adds a custom Criteria predicate.
     *
     * <p>This provides an escape hatch for conditions that are not
     * covered by the built-in lambda operators. Multiple specifications
     * are combined with the built-in DSL conditions using logical AND.</p>
     *
     * @param spec custom predicate definition
     * @return this query
     */
    public LambdaQuery<T> where(QuerySpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        specs.add(spec);
        return this;
    }

    // ==================== Terminal operations ====================

    /**
     * Executes the query and returns all matching entities up to the
     * framework's default list limit.
     *
     * @return matching entities
     */
    public List<T> list() {
        return executor.list(entityClass, conditions, orders, specs);
    }

    /**
     * Executes the query and limits the number of returned entities.
     *
     * @param limit maximum number of results
     * @return matching entities
     */
    public List<T> list(int limit) {
        return executor.list(entityClass, conditions, orders, limit, specs);
    }

    /**
     * Executes the query and expects at most one matching entity.
     *
     * @return the matching entity, or {@code null} if no entity matches
     * @throws HibernateLiteException if more than one entity matches
     */
    public T one() {
        return executor.one(entityClass, conditions, orders, specs);
    }

    /**
     * Counts the number of matching entities.
     *
     * @return number of matching entities
     */
    public long count() {
        return executor.count(entityClass, conditions, specs);
    }

    /**
     * Checks whether at least one entity matches the current conditions.
     *
     * @return {@code true} if at least one entity matches
     */
    public boolean exists() {
        return executor.exists(entityClass, conditions, specs);
    }

    /**
     * Deletes all entities matching the current conditions.
     *
     * <p>The actual deletion is performed by {@link QueryExecutor}.
     * The executor also protects against unconditional deletion.</p>
     *
     * @return number of deleted entities
     */
    public int delete() {
        return executor.delete(entityClass, conditions, specs);
    }

    /**
     * Executes the query as a paginated query.
     *
     * @param request pagination request
     * @return paginated query result
     */
    public Page<T> page(PageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        return executor.page(entityClass, conditions, orders, request, specs);
    }

    // ==================== Internal ====================

    /**
     * Adds a DSL condition to this query.
     *
     * <p>All public condition methods delegate here so that condition
     * creation remains centralized in {@link QueryCondition}.</p>
     */
    private LambdaQuery<T> add(SFunction<T, ?> field, Operator op, Object value) {
        conditions.add(new QueryCondition(field, op, value));
        return this;
    }

}