package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.internal.TransactionTemplate;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.hibernate.HibernateException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Executes JPQL queries through Hibernate.
 *
 * <p>{@code JpqlExecutor} is the execution layer for the JPQL API.
 * {@link JpqlQuery} collects the JPQL string and named parameters,
 * while this class executes the query inside a transaction and
 * converts Hibernate exceptions into framework exceptions.</p>
 *
 * <p>Main responsibilities:</p>
 * <ul>
 *     <li>Execute entity queries using JPQL.</li>
 *     <li>Bind named parameters.</li>
 *     <li>Reuse {@link TransactionTemplate} for transaction handling.</li>
 *     <li>Handle pagination.</li>
 *     <li>Wrap Hibernate exceptions in {@link HibernateLiteException}.</li>
 * </ul>
 *
 * <p>Instances do not maintain per-query mutable state and are
 * therefore safe to share between threads.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/23
 */
public final class JpqlExecutor {

    /**
     * Default maximum number of rows returned by {@link #list} when
     * the caller does not specify an explicit limit.
     */
    private static final int DEFAULT_LIST_LIMIT = 1000;

    private final TransactionTemplate txTemplate;

    /**
     * Creates a JPQL executor.
     *
     * @param txTemplate transaction template used to execute database operations
     * @throws IllegalArgumentException if {@code txTemplate} is {@code null}
     */
    public JpqlExecutor(TransactionTemplate txTemplate) {
        if (txTemplate == null) {
            throw new IllegalArgumentException("TransactionTemplate cannot be null");
        }
        this.txTemplate = txTemplate;
    }

    // ==================== list ====================

    /**
     * Returns matching entities using the default result limit.
     */
    public <T> List<T> list(String jpql,
                            Class<T> resultType,
                            Map<String, Object> params) {
        return list(jpql, resultType, params, DEFAULT_LIST_LIMIT);
    }

    /**
     * Returns matching entities with an explicit maximum result size.
     *
     * @param jpql JPQL query string
     * @param resultType entity type or scalar type (e.g. {@code Long.class}
     *                   for {@code SELECT COUNT(...)})
     * @param params named parameters; may be {@code null} or empty
     * @param limit maximum number of results
     * @param <T> result type
     * @return matching results
     */
    public <T> List<T> list(String jpql,
                            Class<T> resultType,
                            Map<String, Object> params,
                            int limit) {
        requireJpql(jpql);
        requireResultType(resultType);
        requireLimit(limit);

        return wrap("jpql.list", () -> txTemplate.execute(session -> {
            var query = session.createQuery(jpql, resultType);
            bindParams(query, params);
            return query
                    .setMaxResults(limit)
                    .getResultList();
        }));
    }

    // ==================== one ====================

    /**
     * Returns exactly one matching entity, or {@code null} if none.
     *
     * @throws HibernateLiteException if more than one result is found
     */
    public <T> T one(String jpql,
                     Class<T> resultType,
                     Map<String, Object> params) {
        List<T> results = list(jpql, resultType, params, 2);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new HibernateLiteException(
                    "JPQL query returned more than one result for: " + resultType.getName());
        }
        return results.get(0);
    }

    // ==================== count ====================

    /**
     * Executes the JPQL query and returns a single scalar result.
     *
     * <p>Intended for {@code SELECT COUNT(...)} style queries.</p>
     */
    public long count(String jpql,
                      Map<String, Object> params) {
        requireJpql(jpql);

        return wrap("jpql.count", () -> txTemplate.execute(session -> {
            var query = session.createQuery(jpql, Long.class);
            bindParams(query, params);
            Long result = query.getSingleResult();
            return result == null ? 0L : result;
        }));
    }

    // ==================== exists ====================

    /**
     * Checks whether the JPQL query returns at least one row.
     *
     * <p>This is a convenience method: the caller is expected to pass
     * a query that returns an entity or scalar value. {@code exists}
     * simply checks whether {@link #list} yields any result.</p>
     */
    public <T> boolean exists(String jpql,
                              Class<T> resultType,
                              Map<String, Object> params) {
        return !list(jpql, resultType, params, 1).isEmpty();
    }

    // ==================== page ====================

    /**
     * Executes the JPQL query as a paginated query.
     *
     * <p>Two pagination modes are supported:</p>
     * <ul>
     *     <li>Total-count mode: an additional count query is executed.
     *     The caller must provide the count JPQL explicitly.</li>
     *     <li>Slice mode: fetches {@code size + 1} rows to determine
     *     {@code hasNext} without a count query.</li>
     * </ul>
     *
     * @param jpql JPQL query string for the page content
     * @param countJpql JPQL string for the total count; ignored when
     *                  {@link PageRequest#countTotal()} is {@code false}
     * @param resultType entity type or scalar type
     * @param params named parameters
     * @param request pagination request
     * @param <T> result type
     * @return paginated result
     */
    public <T> Page<T> page(String jpql,
                            String countJpql,
                            Class<T> resultType,
                            Map<String, Object> params,
                            PageRequest request) {
        requireJpql(jpql);
        requireResultType(resultType);
        if (request == null) {
            throw new IllegalArgumentException("PageRequest cannot be null");
        }

        return wrap("jpql.page", () -> {
            int fetchSize = request.countTotal()
                    ? request.size()
                    : request.size() + 1;

            List<T> raw = txTemplate.execute(session -> {
                var query = session.createQuery(jpql, resultType);
                bindParams(query, params);
                return query
                        .setFirstResult(request.offset())
                        .setMaxResults(fetchSize)
                        .getResultList();
            });

            if (request.countTotal()) {
                if (countJpql == null || countJpql.isBlank()) {
                    throw new HibernateLiteException(
                            "countJpql must be provided when countTotal is true");
                }
                long total = count(countJpql, params);
                return Page.of(raw, request, total);
            }

            boolean hasNext = raw.size() > request.size();
            List<T> content = hasNext
                    ? raw.subList(0, request.size())
                    : raw;
            return Page.slice(content, request, hasNext);
        });
    }

    // ==================== Internal ====================

    /**
     * Binds named parameters onto a Hibernate query.
     */
    private static void bindParams(org.hibernate.query.Query<?> query,
                                   Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
    }

    private <R> R wrap(String operation, Supplier<R> action) {
        try {
            return action.get();
        } catch (HibernateLiteException e) {
            throw e;
        } catch (HibernateException e) {
            throw new HibernateLiteException(operation + " failed", e);
        }
    }

    private static void requireJpql(String jpql) {
        if (jpql == null || jpql.isBlank()) {
            throw new IllegalArgumentException("jpql cannot be null or blank");
        }
    }

    private static void requireResultType(Class<?> resultType) {
        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }
    }

    private static void requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got: " + limit);
        }
    }
}