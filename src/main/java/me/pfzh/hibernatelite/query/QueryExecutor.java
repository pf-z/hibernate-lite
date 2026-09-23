package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.*;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.internal.TransactionTemplate;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.hibernate.HibernateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Executes Lambda DSL queries through the JPA Criteria API.
 *
 * <p>{@code QueryExecutor} is the execution layer of the query module.
 * {@link LambdaQuery} is responsible for collecting query conditions,
 * ordering clauses, and {@link QuerySpec} instances, while this class
 * translates them into Criteria queries and executes them through
 * Hibernate.</p>
 *
 * <p>Main responsibilities:</p>
 * <ul>
 *     <li>Build and execute entity queries.</li>
 *     <li>Translate query conditions and ordering through {@link QueryBuilder}.</li>
 *     <li>Combine Lambda DSL conditions with {@link QuerySpec} predicates.</li>
 *     <li>Reuse {@link TransactionTemplate} for transaction handling.</li>
 *     <li>Reuse {@link MetadataRegistry} for entity metadata validation.</li>
 *     <li>Execute count, existence, delete, and pagination operations.</li>
 *     <li>Wrap Hibernate exceptions in {@link HibernateLiteException}.</li>
 * </ul>
 *
 * <p>Instances do not maintain per-query mutable state and are therefore
 * safe to share between threads.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
public final class QueryExecutor {

    /**
     * Default maximum number of rows returned by {@link #list} when
     * the caller does not specify an explicit limit.
     *
     * <p>This prevents an unrestricted query from loading an excessively
     * large result set into memory.</p>
     */
    private static final int DEFAULT_LIST_LIMIT = 1000;

    /** Shared registry for entity metadata. */
    private final MetadataRegistry metadataRegistry;

    /** Shared transaction/session lifecycle handler. */
    private final TransactionTemplate txTemplate;

    /**
     * Creates a query executor.
     *
     * @param metadataRegistry shared entity metadata registry
     * @param txTemplate transaction template used to execute database operations
     * @throws IllegalArgumentException if either dependency is {@code null}
     */
    public QueryExecutor(MetadataRegistry metadataRegistry,
                         TransactionTemplate txTemplate) {
        if (metadataRegistry == null) {
            throw new IllegalArgumentException("MetadataRegistry cannot be null");
        }
        if (txTemplate == null) {
            throw new IllegalArgumentException("TransactionTemplate cannot be null");
        }
        this.metadataRegistry = metadataRegistry;
        this.txTemplate = txTemplate;
    }

    // ==================== list ====================

    /**
     * Returns matching entities using the default result limit.
     *
     * <p>This is equivalent to calling {@link #list} with a limit of
     * {@link #DEFAULT_LIST_LIMIT}.</p>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param orders ordering clauses
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return matching entities
     */
    public <T> List<T> list(Class<T> clazz,
                            List<QueryCondition> conditions,
                            List<QueryOrder> orders,
                            List<QuerySpec<T>> specs) {
        return list(clazz, conditions, orders, DEFAULT_LIST_LIMIT, specs);
    }

    /**
     * Returns matching entities with an explicit maximum result size.
     *
     * <p>The query is constructed using the JPA Criteria API and executed
     * inside {@link TransactionTemplate}. Lambda DSL conditions and
     * {@link QuerySpec} predicates are combined with {@code AND}.</p>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param orders ordering clauses
     * @param limit maximum number of results to return
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return matching entities
     */
    public <T> List<T> list(Class<T> clazz,
                            List<QueryCondition> conditions,
                            List<QueryOrder> orders,
                            int limit,
                            List<QuerySpec<T>> specs) {
        requireClass(clazz);
        requireLimit(limit);

        /*
         * Resolve and validate entity metadata before building the
         * Criteria query.
         *
         * QueryBuilder uses this metadata to validate property names.
         */
        EntityMeta meta = metadataRegistry.get(clazz);

        /*
         * wrap() converts Hibernate exceptions into the framework's
         * HibernateLiteException.
         *
         * TransactionTemplate handles Session and transaction lifecycle.
         */
        return wrap("query.list", () -> txTemplate.execute(session -> {

            /*
             * CriteriaBuilder creates the objects used to build
             * the JPA Criteria query.
             */
            CriteriaBuilder cb = session.getCriteriaBuilder();

            /*
             * Create a typed CriteriaQuery for the entity type.
             */
            CriteriaQuery<T> cq = cb.createQuery(clazz);

            /*
             * Define the entity being queried.
             *
             * For User.class, root represents User.
             */
            Root<T> root = cq.from(clazz);

            /*
             * Combine:
             *
             * 1. conditions from the Lambda DSL
             * 2. predicates supplied through QuerySpec
             *
             * into one Predicate.
             */
            Predicate where = combine(cb, root, conditions, specs, meta);

            /*
             * Only add a WHERE clause when at least one
             * restriction exists.
             */
            if (where != null) {
                cq.where(where);
            }

            /*
             * Apply all requested ORDER BY clauses.
             */
            QueryBuilder.applyOrders(cb, cq, root, orders, meta);

            /*
             * Convert the CriteriaQuery into a Hibernate query,
             * apply the maximum result limit, and execute it.
             */
            return session.createQuery(cq)
                    .setMaxResults(limit)
                    .getResultList();
        }));
    }

    // ==================== one ====================

    /**
     * Returns exactly one matching entity, or {@code null} if no entity
     * matches.
     *
     * <p>At most two rows are fetched. This allows the executor to
     * distinguish between zero, one, and multiple matching entities
     * without loading the entire result set.</p>
     *
     * <ul>
     *     <li>0 results → {@code null}</li>
     *     <li>1 result → return the entity</li>
     *     <li>2 results → throw {@link HibernateLiteException}</li>
     * </ul>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param orders ordering clauses
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return the single matching entity, or {@code null}
     * @throws HibernateLiteException if more than one entity matches
     */
    public <T> T one(Class<T> clazz,
                     List<QueryCondition> conditions,
                     List<QueryOrder> orders,
                     List<QuerySpec<T>> specs) {
        /*
         * Fetch at most two rows.
         *
         * Fetching two rather than one is important because one result
         * cannot distinguish "exactly one match" from "many matches".
         */
        List<T> results = list(clazz, conditions, orders, 2, specs);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new HibernateLiteException(
                    "Query returned more than one result for: " + clazz.getName());
        }
        return results.get(0);
    }

    // ==================== count ====================

    /**
     * Counts the number of entities matching the given conditions.
     *
     * <p>The filtering logic is shared with normal queries through
     * {@link #combine}.</p>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return number of matching entities
     */
    public <T> long count(Class<T> clazz,
                          List<QueryCondition> conditions,
                          List<QuerySpec<T>> specs) {
        requireClass(clazz);
        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.count", () -> txTemplate.execute(session -> {

            /*
             * Create a typed CriteriaQuery whose result type
             * is Long because COUNT returns a number.
             */
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(clazz);

            /*
             * SELECT COUNT(root)
             */
            cq.select(cb.count(root));

            /*
             * Reuse the same condition-combining logic used
             * by normal entity queries.
             */
            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cq.where(where);
            }

            Long result = session.createQuery(cq).getSingleResult();

            /*
             * COUNT should normally never return null, but
             * keep a defensive fallback.
             */
            return result == null ? 0L : result;
        }));
    }

    // ==================== exists ====================

    /**
     * Checks whether at least one entity matches the given conditions.
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return {@code true} if at least one entity matches
     */
    public <T> boolean exists(Class<T> clazz,
                              List<QueryCondition> conditions,
                              List<QuerySpec<T>> specs) {
        /*
         * The current implementation reuses count().
         *
         * If the count is greater than zero, at least one matching
         * entity exists.
         */
        return count(clazz, conditions, specs) > 0;
    }

    // ==================== delete ====================

    /**
     * Deletes all entities matching the given conditions.
     *
     * <p>The deletion is executed as a bulk DELETE statement rather
     * than loading entities into memory one by one.</p>
     *
     * <p>At least one Lambda DSL condition or {@link QuerySpec} is
     * required. Unconditional deletion is rejected to prevent accidental
     * deletion of the entire table.</p>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return number of deleted rows
     * @throws HibernateLiteException if no deletion restriction is supplied
     */
    public <T> int delete(Class<T> clazz,
                          List<QueryCondition> conditions,
                          List<QuerySpec<T>> specs) {
        requireClass(clazz);

        /*
         * Safety check:
         *
         *     db.query(User.class).delete()
         *
         * must not silently execute:
         *
         *     DELETE FROM user
         *
         * Requiring at least one restriction makes accidental
         * full-table deletion impossible through this API.
         */
        if (conditions.isEmpty() && (specs == null || specs.isEmpty())) {
            throw new HibernateLiteException(
                    "delete() requires at least one condition or where() clause; "
                            + "refusing to delete all rows of " + clazz.getName());
        }

        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.delete", () -> txTemplate.execute(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();

            /*
             * CriteriaDelete is the JPA Criteria API equivalent
             * of a DELETE statement.
             */
            CriteriaDelete<T> cd = cb.createCriteriaDelete(clazz);

            /*
             * Define the entity from which rows will be deleted.
             */
            Root<T> root = cd.from(clazz);

            /*
             * Build the combined WHERE predicate using the
             * same logic as SELECT and COUNT.
             */
            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cd.where(where);
            }

            /*
             * Execute the bulk DELETE operation.
             *
             * executeUpdate() returns the number of affected rows.
             */
            return session.createMutationQuery(cd).executeUpdate();
        }));
    }

    // ==================== page ====================

    /**
     * Returns a page of matching entities.
     *
     * <p>Two pagination modes are supported:</p>
     *
     * <ul>
     *     <li>
     *         <b>Total-count mode:</b>
     *         when {@link PageRequest#countTotal()} is {@code true},
     *         the executor performs the page query and a separate COUNT
     *         query. The total element count is then stored in the result.
     *     </li>
     *     <li>
     *         <b>Slice mode:</b>
     *         when total counting is disabled, the executor fetches
     *         {@code size + 1} rows. The extra row is used to determine
     *         whether another page exists and is then removed from the
     *         returned content.
     *     </li>
     * </ul>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param orders ordering clauses
     * @param request pagination request
     * @param specs advanced Criteria predicates
     * @param <T> entity type
     * @return paginated query result
     */
    public <T> Page<T> page(Class<T> clazz,
                            List<QueryCondition> conditions,
                            List<QueryOrder> orders,
                            PageRequest request,
                            List<QuerySpec<T>> specs) {
        requireClass(clazz);

        if (request == null) {
            throw new IllegalArgumentException("PageRequest cannot be null");
        }

        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.page", () -> {

            /*
             * When total counting is enabled, we only need the requested
             * page size.
             *
             * When total counting is disabled, fetch one extra row so
             * that hasNext can be determined without a COUNT query.
             */
            int fetchSize = request.countTotal()
                    ? request.size()
                    : request.size() + 1;

            /*
             * Execute the actual page query.
             */
            List<T> raw = txTemplate.execute(session -> {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaQuery<T> cq = cb.createQuery(clazz);
                Root<T> root = cq.from(clazz);

                /*
                 * Build the WHERE clause from both the Lambda DSL and
                 * advanced QuerySpec predicates.
                 */
                Predicate where = combine(cb, root, conditions, specs, meta);
                if (where != null) {
                    cq.where(where);
                }

                /*
                 * Apply ORDER BY before applying pagination.
                 */
                QueryBuilder.applyOrders(cb, cq, root, orders, meta);

                /*
                 * offset() determines where the page starts.
                 *
                 * fetchSize is either:
                 *
                 *     size
                 *
                 * or:
                 *
                 *     size + 1
                 *
                 * depending on whether total counting is enabled.
                 */
                return session.createQuery(cq)
                        .setFirstResult(request.offset())
                        .setMaxResults(fetchSize)
                        .getResultList();
            });

            /*
             * Total-count mode:
             *
             * The page query gives us the current page content.
             * A separate COUNT query gives us the total number of
             * matching entities.
             */
            if (request.countTotal()) {
                long total = count(clazz, conditions, specs);
                return Page.of(raw, request, total);
            }

            /*
             * Slice mode:
             *
             * If we received more rows than requested, the extra row
             * proves that another page exists.
             */
            boolean hasNext = raw.size() > request.size();

            /*
             * Remove the extra row before returning the page content.
             */
            List<T> content = hasNext
                    ? raw.subList(0, request.size())
                    : raw;
            return Page.slice(content, request, hasNext);
        });
    }

    // ==================== Internal ====================

    /**
     * Combines all query restrictions into a single JPA Predicate.
     *
     * <p>Conditions created through the Lambda DSL are first converted
     * by {@link QueryBuilder}. Each {@link QuerySpec} then contributes
     * an additional predicate. All non-null predicates are combined
     * using {@code AND}.</p>
     *
     * <p>If no restriction exists, {@code null} is returned, allowing
     * the caller to omit the WHERE clause entirely.</p>
     *
     * @param cb criteria builder
     * @param root query root
     * @param conditions Lambda DSL conditions
     * @param specs advanced Criteria predicates
     * @param meta entity metadata
     * @param <T> entity type
     * @return combined predicate, or {@code null} if there are no restrictions
     */
    private <T> Predicate combine(CriteriaBuilder cb,
                                  Root<T> root,
                                  List<QueryCondition> conditions,
                                  List<QuerySpec<T>> specs,
                                  EntityMeta meta) {
        List<Predicate> parts = new ArrayList<>();

        /*
         * Convert the normal Lambda DSL conditions into a single
         * Predicate.
         *
         * QueryBuilder is responsible for translating individual
         * operators such as EQ, LIKE, IN, and BETWEEN.
         */
        Predicate dsl = QueryBuilder.buildPredicate(cb, root, conditions, meta);
        if (dsl != null) {
            parts.add(dsl);
        }

        /*
         * Add predicates supplied through the escape-hatch QuerySpec API.
         */
        if (specs != null) {
            for (QuerySpec<T> spec : specs) {
                Predicate p = spec.toPredicate(cb, root);

                /*
                 * A QuerySpec is allowed to return null, meaning that
                 * it contributes no restriction.
                 */
                if (p != null) {
                    parts.add(p);
                }
            }
        }

        /*
         * No restrictions:
         *
         *     SELECT ...
         *
         * without WHERE.
         */
        if (parts.isEmpty()) {
            return null;
        }

        /*
         * Only one restriction:
         *
         *     WHERE predicate
         *
         * No need to create an additional AND expression.
         */
        if (parts.size() == 1) {
            return parts.get(0);
        }

        /*
         * Multiple restrictions are combined with AND.
         *
         * For example:
         *
         *     age = 18
         *     AND status = 'ACTIVE'
         *     AND name LIKE 'A%'
         */
        return cb.and(parts.toArray(new Predicate[0]));
    }

    /**
     * Executes an operation and converts Hibernate exceptions into
     * the framework's unified exception type.
     *
     * <p>Existing {@link HibernateLiteException} instances are rethrown
     * unchanged so that they are not wrapped multiple times.</p>
     *
     * @param operation logical operation name used in error messages
     * @param action operation to execute
     * @param <R> result type
     * @return operation result
     * @throws HibernateLiteException if a Hibernate operation fails
     */
    private <R> R wrap(String operation, Supplier<R> action) {
        try {
            return action.get();
        } catch (HibernateLiteException e) {

            /*
             * The exception is already in the framework's public
             * exception type, so preserve it unchanged.
             */
            throw e;
        } catch (HibernateException e) {

            /*
             * Convert Hibernate's exception hierarchy into the
             * framework's unified exception type.
             */
            throw new HibernateLiteException(operation + " failed", e);
        }
    }

    /**
     * Validates the entity type argument.
     *
     * @param clazz entity type
     * @throws IllegalArgumentException if {@code clazz} is null
     */
    private static void requireClass(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("entity type cannot be null");
        }
    }

    /**
     * Validates a query result limit.
     *
     * @param limit maximum number of results
     * @throws IllegalArgumentException if the limit is not positive
     */
    private static void requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got: " + limit);
        }
    }

    /**
     * Updates all entities matching the given conditions.
     *
     * <p>The update is executed as a bulk UPDATE statement rather than
     * loading entities into memory one by one.</p>
     *
     * <p>At least one Lambda DSL condition or {@link QuerySpec} is
     * required. Unconditional updates are rejected to prevent accidental
     * modification of the entire table.</p>
     *
     * @param clazz entity type
     * @param conditions Lambda DSL conditions
     * @param specs advanced Criteria predicates
     * @param updates map of field name to new value
     * @param <T> entity type
     * @return number of updated rows
     * @throws HibernateLiteException if no update restriction is supplied
     */
    public <T> int update(Class<T> clazz,
                          List<QueryCondition> conditions,
                          List<QuerySpec<T>> specs,
                          Map<String, Object> updates) {
        requireClass(clazz);

        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("updates cannot be null or empty");
        }

        // Safety: refuse unconditional update
        if (conditions.isEmpty() && (specs == null || specs.isEmpty())) {
            throw new HibernateLiteException(
                    "update() requires at least one condition or where() clause; "
                            + "refusing to update all rows of " + clazz.getName());
        }

        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.update", () -> txTemplate.execute(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaUpdate<T> cu = cb.createCriteriaUpdate(clazz);
            Root<T> root = cu.from(clazz);

            for (Map.Entry<String, Object> e : updates.entrySet()) {
                if (meta.getField(e.getKey()) == null) {
                    throw new HibernateLiteException(
                            "Entity " + clazz.getName() + " has no field: " + e.getKey());
                }
                cu.set(root.get(e.getKey()), e.getValue());
            }

            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cu.where(where);
            }

            return session.createMutationQuery(cu).executeUpdate();
        }));
    }

}