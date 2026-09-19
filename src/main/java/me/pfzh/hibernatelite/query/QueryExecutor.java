package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.internal.TransactionTemplate;
import me.pfzh.hibernatelite.metadata.EntityMeta;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import me.pfzh.hibernatelite.pagination.Page;
import me.pfzh.hibernatelite.pagination.PageRequest;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Executes Lambda DSL queries through JPA Criteria.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Translate DSL conditions and ordering into Criteria.</li>
 *     <li>Reuse {@link TransactionTemplate} for automatic transaction handling.</li>
 *     <li>Wrap Hibernate exceptions into {@link HibernateLiteException}.</li>
 *     <li>Handle pagination and optional count queries.</li>
 * </ul>
 *
 * <p>Instances are stateless and thread-safe.</p>
 *
 */
public final class QueryExecutor {

    /** Default upper bound for {@link #list} without explicit limit. */
    private static final int DEFAULT_LIST_LIMIT = 1000;

    private final MetadataRegistry metadataRegistry;
    private final TransactionTemplate txTemplate;

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

    public <T> List<T> list(Class<T> clazz,
                            List<QueryCondition> conditions,
                            List<QueryOrder> orders,
                            List<QuerySpec<T>> specs) {
        return list(clazz, conditions, orders, DEFAULT_LIST_LIMIT, specs);
    }

    public <T> List<T> list(Class<T> clazz,
                            List<QueryCondition> conditions,
                            List<QueryOrder> orders,
                            int limit,
                            List<QuerySpec<T>> specs) {
        requireClass(clazz);
        requireLimit(limit);
        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.list", () -> txTemplate.execute(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(clazz);
            Root<T> root = cq.from(clazz);

            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cq.where(where);
            }
            QueryBuilder.applyOrders(cb, cq, root, orders, meta);

            return session.createQuery(cq)
                    .setMaxResults(limit)
                    .getResultList();
        }));
    }

    // ==================== one ====================

    public <T> T one(Class<T> clazz,
                     List<QueryCondition> conditions,
                     List<QueryOrder> orders,
                     List<QuerySpec<T>> specs) {
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

    public <T> long count(Class<T> clazz,
                          List<QueryCondition> conditions,
                          List<QuerySpec<T>> specs) {
        requireClass(clazz);
        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.count", () -> txTemplate.execute(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(clazz);
            cq.select(cb.count(root));

            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cq.where(where);
            }

            Long result = session.createQuery(cq).getSingleResult();
            return result == null ? 0L : result;
        }));
    }

    // ==================== exists ====================

    public <T> boolean exists(Class<T> clazz,
                              List<QueryCondition> conditions,
                              List<QuerySpec<T>> specs) {
        return count(clazz, conditions, specs) > 0;
    }

    // ==================== delete ====================

    /**
     * Deletes all matching entities in a single SQL statement.
     *
     * <p>At least one condition or escape-hatch {@code QuerySpec} is
     * required. Attempting to delete without any restriction throws
     * {@link HibernateLiteException} to prevent accidental full-table
     * deletion.</p>
     *
     * @return number of deleted rows
     */
    public <T> int delete(Class<T> clazz,
                          List<QueryCondition> conditions,
                          List<QuerySpec<T>> specs) {
        requireClass(clazz);

        // Safety: refuse unconditional delete
        if (conditions.isEmpty() && (specs == null || specs.isEmpty())) {
            throw new HibernateLiteException(
                    "delete() requires at least one condition or where() clause; "
                            + "refusing to delete all rows of " + clazz.getName());
        }

        EntityMeta meta = metadataRegistry.get(clazz);

        return wrap("query.delete", () -> txTemplate.execute(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaDelete<T> cd = cb.createCriteriaDelete(clazz);
            Root<T> root = cd.from(clazz);

            Predicate where = combine(cb, root, conditions, specs, meta);
            if (where != null) {
                cd.where(where);
            }

            return session.createMutationQuery(cd).executeUpdate();
        }));
    }

    // ==================== page ====================

    /**
     * Returns a page of matching entities.
     *
     * <p>When {@link PageRequest#countTotal()} is {@code true}, the total
     * element count is computed in a second query and {@code hasNext} is
     * derived from it.</p>
     *
     * <p>When total count is disabled, the executor fetches
     * {@code size + 1} rows to determine {@code hasNext} accurately, then
     * trims the extra row before returning.</p>
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
            // Fetch one extra row when total count is disabled so that
            // hasNext can be determined accurately.
            int fetchSize = request.countTotal()
                    ? request.size()
                    : request.size() + 1;

            List<T> raw = txTemplate.execute(session -> {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaQuery<T> cq = cb.createQuery(clazz);
                Root<T> root = cq.from(clazz);

                Predicate where = combine(cb, root, conditions, specs, meta);
                if (where != null) {
                    cq.where(where);
                }
                QueryBuilder.applyOrders(cb, cq, root, orders, meta);

                return session.createQuery(cq)
                        .setFirstResult(request.offset())
                        .setMaxResults(fetchSize)
                        .getResultList();
            });

            if (request.countTotal()) {
                long total = count(clazz, conditions, specs);
                return Page.of(raw, request, total);
            }

            // Slice mode: trim the extra row and compute hasNext.
            boolean hasNext = raw.size() > request.size();
            List<T> content = hasNext
                    ? raw.subList(0, request.size())
                    : raw;
            return Page.slice(content, request, hasNext);
        });
    }

    // ==================== 内部 ====================

    private <T> Predicate combine(CriteriaBuilder cb,
                                  Root<T> root,
                                  List<QueryCondition> conditions,
                                  List<QuerySpec<T>> specs,
                                  EntityMeta meta) {
        List<Predicate> parts = new ArrayList<>();

        Predicate dsl = QueryBuilder.buildPredicate(cb, root, conditions, meta);
        if (dsl != null) {
            parts.add(dsl);
        }

        if (specs != null) {
            for (QuerySpec<T> spec : specs) {
                Predicate p = spec.toPredicate(cb, root);
                if (p != null) {
                    parts.add(p);
                }
            }
        }

        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return cb.and(parts.toArray(new Predicate[0]));
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

    private static void requireClass(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("entity type cannot be null");
        }
    }

    private static void requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got: " + limit);
        }
    }
}