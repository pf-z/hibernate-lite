package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.metadata.EntityMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates Lambda DSL conditions and ordering into JPA Criteria.
 *
 * <p>This class is stateless. It also validates that every referenced
 * field actually exists in the target entity, so typos fail fast with
 * a clear message instead of producing a runtime Hibernate error.</p>
 *
 * <p><b>Note:</b> comparisons on {@code GT} / {@code GE} / {@code LT} /
 * {@code LE} / {@code BETWEEN} require the underlying column value to
 * be {@link Comparable}. Type mismatches are reported by Hibernate at
 * execution time.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryBuilder {

    private QueryBuilder() {}

    /**
     * Builds a WHERE predicate combining all conditions with AND.
     *
     * @return the combined predicate, or {@code null} if there are no
     *         conditions
     */
    static <T> Predicate buildPredicate(CriteriaBuilder cb,
                                        Root<T> root,
                                        List<QueryCondition> conditions,
                                        EntityMeta meta) {
        if (conditions.isEmpty()) {
            return null;
        }

        List<Predicate> predicates = new ArrayList<>(conditions.size());
        for (QueryCondition c : conditions) {
            validateField(c.fieldName(), meta);
            predicates.add(buildOne(cb, root, c));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Applies ordering clauses to the query.
     */
    static <T> void applyOrders(CriteriaBuilder cb,
                                CriteriaQuery<T> cq,
                                Root<T> root,
                                List<QueryOrder> orders,
                                EntityMeta meta) {
        if (orders.isEmpty()) {
            return;
        }

        List<jakarta.persistence.criteria.Order> criteriaOrders =
                new ArrayList<>(orders.size());
        for (QueryOrder o : orders) {
            validateField(o.fieldName(), meta);
            Path<Object> path = root.get(o.fieldName());
            criteriaOrders.add(o.ascending() ? cb.asc(path) : cb.desc(path));
        }
        cq.orderBy(criteriaOrders);
    }

    // ==================== 单个条件翻译 ====================

    /**
     * Translates one condition into a predicate.
     *
     * <p>The {@code rawtypes} and {@code unchecked} suppressions are
     * required because JPA's {@link CriteriaBuilder} generic bounds on
     * {@code Comparable} cannot be satisfied by a runtime {@code Object}
     * value. {@link QueryCondition} already validates the value at
     * construction time.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Predicate buildOne(CriteriaBuilder cb,
                                          Root<T> root,
                                          QueryCondition c) {
        Path<Object> path = root.get(c.fieldName());
        Object value = c.value();

        return switch (c.operator()) {
            case EQ          -> cb.equal(path, value);
            case NE          -> cb.notEqual(path, value);
            case GT          -> cb.greaterThan((Expression) path, (Comparable) value);
            case GE          -> cb.greaterThanOrEqualTo((Expression) path, (Comparable) value);
            case LT          -> cb.lessThan((Expression) path, (Comparable) value);
            case LE          -> cb.lessThanOrEqualTo((Expression) path, (Comparable) value);
            case LIKE        -> cb.like(path.as(String.class), (String) value);
            case IN          -> buildIn(cb, path, (Iterable<?>) value);
            case NOT_IN      -> cb.not(buildIn(cb, path, (Iterable<?>) value));
            case IS_NULL     -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            case BETWEEN     -> {
                Object[] arr = (Object[]) value;
                yield cb.between((Expression) path,
                        (Comparable) arr[0], (Comparable) arr[1]);
            }
        };
    }

    private static CriteriaBuilder.In<Object> buildIn(CriteriaBuilder cb,
                                                      Path<Object> path,
                                                      Iterable<?> values) {
        CriteriaBuilder.In<Object> in = cb.in(path);
        boolean empty = true;
        for (Object v : values) {
            in.value(v);
            empty = false;
        }
        if (empty) {
            throw new HibernateLiteException(
                    "IN / NOT IN requires a non-empty collection");
        }
        return in;
    }

    /**
     * Validates that a field exists in the entity metadata.
     *
     * <p>Package-private so it can be tested directly.</p>
     */
    static void validateField(String fieldName, EntityMeta meta) {
        if (meta.getField(fieldName) == null) {
            throw new HibernateLiteException(
                    "Entity " + meta.getEntityClass().getName()
                            + " has no field: " + fieldName);
        }
    }

}