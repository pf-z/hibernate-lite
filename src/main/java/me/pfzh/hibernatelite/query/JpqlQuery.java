package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe fluent facade for JPQL queries.
 *
 * <p>A {@code JpqlQuery} is created through
 * {@code db.query(jpql, EntityClass.class)}. Named parameters are
 * collected first, and the query is executed only when a terminal
 * operation such as {@link #list()}, {@link #one()}, or
 * {@link #count()} is called.</p>
 *
 * <p>This class is a query API facade rather than the actual query
 * executor. Execution is delegated to {@link JpqlExecutor}, which
 * handles transaction lifecycle and Hibernate exception wrapping.</p>
 *
 * <p><b>Not thread-safe.</b> Each query should be created, configured,
 * and executed within a single thread.</p>
 *
 * @param <T> result type
 *
 * @author Pengfei Zhang
 * @since 2026/9/23
 */
public final class JpqlQuery<T> {

    private final String jpql;
    private final Class<T> resultType;
    private final JpqlExecutor executor;
    private final Map<String, Object> params = new LinkedHashMap<>();

    /**
     * <b>Internal API.</b> Created by
     * {@code DataStore.query(jpql, resultType)}.
     */
    public JpqlQuery(String jpql, Class<T> resultType, JpqlExecutor executor) {
        if (jpql == null || jpql.isBlank()) {
            throw new IllegalArgumentException("jpql cannot be null or blank");
        }
        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        this.jpql = jpql;
        this.resultType = resultType;
        this.executor = executor;
    }

    // ==================== Parameters ====================

    /**
     * Binds a named parameter.
     *
     * @param name parameter name (without the leading {@code :})
     * @param value parameter value; may be {@code null}
     * @return this query
     */
    public JpqlQuery<T> param(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name cannot be null or blank");
        }
        params.put(name, value);
        return this;
    }

    // ==================== Terminal operations ====================

    /**
     * Executes the query and returns all matching results up to the
     * framework's default list limit.
     */
    public List<T> list() {
        return executor.list(jpql, resultType, params);
    }

    /**
     * Executes the query and limits the number of returned results.
     */
    public List<T> list(int limit) {
        return executor.list(jpql, resultType, params, limit);
    }

    /**
     * Executes the query and expects at most one result.
     *
     * @return the single result, or {@code null} if no result matches
     * @throws me.pfzh.hibernatelite.exception.HibernateLiteException
     *         if more than one result matches
     */
    public T one() {
        return executor.one(jpql, resultType, params);
    }

    /**
     * Executes the query and returns a single scalar count.
     *
     * <p>Intended for {@code SELECT COUNT(...)} style JPQL.</p>
     */
    public long count() {
        return executor.count(jpql, params);
    }

    /**
     * Checks whether the query returns at least one row.
     */
    public boolean exists() {
        return executor.exists(jpql, resultType, params);
    }

    /**
     * Executes the query as a paginated query.
     *
     * <p>When {@link PageRequest#countTotal()} is {@code true}, the
     * caller must supply a count JPQL through
     * {@link #page(String, PageRequest)}.</p>
     */
    public Page<T> page(PageRequest request) {
        throw new UnsupportedOperationException(
                "A count JPQL is required for total-count mode. "
                        + "Use page(countJpql, request).");
    }

    /**
     * Executes the query as a paginated query with an explicit count JPQL.
     *
     * @param countJpql JPQL string for total counting; ignored when
     *                  {@link PageRequest#countTotal()} is {@code false}
     * @param request pagination request
     */
    public Page<T> page(String countJpql, PageRequest request) {
        return executor.page(jpql, countJpql, resultType, params, request);
    }
}