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
 * Translates the internal query representation into JPA Criteria API
 * expressions.
 *
 * <p>{@code QueryBuilder} is the bridge between the Hibernate-Lite
 * Lambda DSL and the JPA Criteria API.</p>
 *
 * <p>For example, a condition such as:</p>
 *
 * <pre>{@code
 * .eq(User::getAge, 18)
 * }</pre>
 *
 * <p>is first represented as a {@link QueryCondition}, and then
 * translated into an equivalent JPA Criteria predicate such as:</p>
 *
 * <pre>{@code
 * cb.equal(root.get("age"), 18)
 * }</pre>
 *
 * <p>This class is stateless and therefore contains only static
 * utility methods. It also validates that every referenced field
 * actually exists in the target entity, so field-name errors fail
 * fast with a clear framework exception.</p>
 *
 * <p><b>Note:</b> comparisons using {@code GT}, {@code GE}, {@code LT},
 * {@code LE}, or {@code BETWEEN} require values that are compatible
 * with JPA's {@link Comparable}-based comparison methods. Detailed
 * type compatibility is ultimately handled by JPA/Hibernate.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryBuilder {

    /**
     * Utility class; instantiation is not required.
     */
    private QueryBuilder() {}

    /**
     * Builds a WHERE predicate from all Lambda DSL conditions.
     *
     * <p>All conditions are combined with logical {@code AND}.</p>
     *
     * <p>For example:</p>
     *
     * <pre>{@code
     * .eq(User::getAge, 18)
     * .eq(User::getStatus, "ACTIVE")
     * .like(User::getName, "A%")
     * }</pre>
     *
     * <p>is translated into the equivalent of:</p>
     *
     * <pre>
     * age = 18
     * AND status = 'ACTIVE'
     * AND name LIKE 'A%'
     * </pre>
     *
     * @param cb         JPA Criteria builder
     * @param root       query root representing the entity
     * @param conditions internal query conditions
     * @param meta       entity metadata used for field validation
     * @param <T>        entity type
     * @return the combined predicate, or {@code null} if there are
     *         no conditions
     */
    static <T> Predicate buildPredicate(CriteriaBuilder cb,
                                        Root<T> root,
                                        List<QueryCondition> conditions,
                                        EntityMeta meta) {

        /*
         * No conditions means that the query has no WHERE restriction.
         *
         * Returning null allows the caller to simply omit cq.where(...).
         */
        if (conditions.isEmpty()) {
            return null;
        }

        /*
         * Each QueryCondition is translated into one JPA Predicate.
         */
        List<Predicate> predicates = new ArrayList<>(conditions.size());
        for (QueryCondition c : conditions) {

            /*
             * Validate the field before asking JPA to resolve it.
             *
             * This gives the framework a clearer error message when
             * a field name is invalid.
             */
            validateField(c.fieldName(), meta);

            /*
             * Translate one internal condition into a Criteria Predicate.
             */
            predicates.add(buildOne(cb, root, c));
        }

        /*
         * Combine all conditions with logical AND.
         */
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Applies all ordering clauses to a Criteria query.
     *
     * <p>Each {@link QueryOrder} is translated into either
     * {@code cb.asc(...)} or {@code cb.desc(...)}.</p>
     *
     * @param cb     JPA Criteria builder
     * @param cq     Criteria query being configured
     * @param root   query root representing the entity
     * @param orders internal ordering clauses
     * @param meta   entity metadata used for field validation
     * @param <T>    entity type
     */
    static <T> void applyOrders(CriteriaBuilder cb,
                                CriteriaQuery<T> cq,
                                Root<T> root,
                                List<QueryOrder> orders,
                                EntityMeta meta) {

        /*
         * No ordering requested.
         */
        if (orders.isEmpty()) {
            return;
        }

        /*
         * JPA Criteria expects a list of Order objects.
         */
        List<jakarta.persistence.criteria.Order> criteriaOrders =
                new ArrayList<>(orders.size());
        for (QueryOrder o : orders) {

            /*
             * Validate the referenced entity field before creating
             * the Criteria path.
             */
            validateField(o.fieldName(), meta);

            /*
             * root.get("fieldName") represents the entity attribute
             * being sorted.
             */
            Path<Object> path = root.get(o.fieldName());

            /*
             * Convert the internal ascending/descending flag into
             * the corresponding JPA Criteria ordering expression.
             */
            criteriaOrders.add(o.ascending() ? cb.asc(path) : cb.desc(path));
        }

        /*
         * Apply all ordering clauses to the CriteriaQuery.
         *
         * Their order is preserved, so multiple orderBy calls result
         * in a stable sequence such as:
         *
         * ORDER BY name ASC, age DESC
         */
        cq.orderBy(criteriaOrders);
    }

    // ==================== Single condition translation ====================

    /**
     * Translates one {@link QueryCondition} into a JPA Criteria
     * {@link Predicate}.
     *
     * <p>This method is the central operator translation layer of the
     * query module. Each {@link Operator} is mapped to the corresponding
     * JPA CriteriaBuilder operation.</p>
     *
     * <p>For example:</p>
     *
     * <pre>{@code
     * EQ       -> cb.equal(...)
     * GT       -> cb.greaterThan(...)
     * LIKE     -> cb.like(...)
     * IS_NULL  -> cb.isNull(...)
     * }</pre>
     *
     * <p>The {@code rawtypes} and {@code unchecked} suppressions are
     * required because JPA's CriteriaBuilder comparison methods use
     * generic {@link Comparable} bounds, while the framework stores
     * dynamically resolved fields and values as {@code Object}.</p>
     *
     * <p>The structural validation of condition values has already been
     * performed by {@link QueryCondition}. Actual field/value type
     * compatibility is ultimately checked by JPA/Hibernate.</p>
     *
     * @param cb    JPA Criteria builder
     * @param root  query root representing the entity
     * @param c     condition to translate
     * @param <T>   entity type
     * @return the corresponding JPA predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Predicate buildOne(CriteriaBuilder cb,
                                          Root<T> root,
                                          QueryCondition c) {

        /*
         * Resolve the entity attribute referenced by the condition.
         *
         * At this point validateField(...) has already confirmed that
         * the field exists in the entity metadata.
         */
        Path<Object> path = root.get(c.fieldName());
        Object value = c.value();

        /*
         * Translate the internal Operator into the corresponding
         * CriteriaBuilder operation.
         */
        return switch (c.operator()) {

            /** Equality: {@code field = value}. */
            case EQ          -> cb.equal(path, value);

            /** Inequality: {@code field <> value}. */
            case NE          -> cb.notEqual(path, value);

            /** Greater than: {@code field > value}. */
            case GT          -> cb.greaterThan((Expression) path, (Comparable) value);

            /** Greater than or equal: {@code field >= value}. */
            case GE          -> cb.greaterThanOrEqualTo((Expression) path, (Comparable) value);

            /** Less than: {@code field < value}. */
            case LT          -> cb.lessThan((Expression) path, (Comparable) value);

            /** Less than or equal: {@code field <= value}. */
            case LE          -> cb.lessThanOrEqualTo((Expression) path, (Comparable) value);

            /** String pattern matching: {@code field LIKE value}. */
            case LIKE        -> cb.like(path.as(String.class), (String) value);

            /** Membership: {@code field IN (...) }. */
            case IN          -> buildIn(cb, path, (Iterable<?>) value);

            /** Negated membership: {@code field NOT IN (...) }. */
            case NOT_IN      -> cb.not(buildIn(cb, path, (Iterable<?>) value));

            /** Null check: {@code field IS NULL}. */
            case IS_NULL     -> cb.isNull(path);

            /** Non-null check: {@code field IS NOT NULL}. */
            case IS_NOT_NULL -> cb.isNotNull(path);

            /** Range comparison: {@code field BETWEEN low AND high}. */
            case BETWEEN     -> {
                Object[] arr = (Object[]) value;
                yield cb.between((Expression) path,
                        (Comparable) arr[0], (Comparable) arr[1]);
            }
        };
    }

    /**
     * Builds a JPA {@code IN} predicate from an iterable collection.
     *
     * <p>For example:</p>
     *
     * <pre>{@code
     * .in(User::getAge, List.of(18, 19, 20))
     * }</pre>
     *
     * <p>is translated into the equivalent of:</p>
     *
     * <pre>
     * age IN (18, 19, 20)
     * </pre>
     *
     * <p>Empty collections are rejected because an empty SQL
     * {@code IN} list is not portable across databases and may result
     * in invalid SQL.</p>
     *
     * @param cb     JPA Criteria builder
     * @param path   entity attribute being tested
     * @param values  values used by the IN expression
     * @return the constructed IN predicate
     * @throws HibernateLiteException if {@code values} is empty
     */
    private static CriteriaBuilder.In<Object> buildIn(CriteriaBuilder cb,
                                                      Path<Object> path,
                                                      Iterable<?> values) {

        /*
         * Create an empty JPA IN predicate and add values one by one.
         */
        CriteriaBuilder.In<Object> in = cb.in(path);
        boolean empty = true;
        for (Object v : values) {

            /*
             * Add one value to:
             *
             *     field IN (...)
             */
            in.value(v);
            empty = false;
        }

        /*
         * Reject empty IN / NOT IN collections.
         */
        if (empty) {
            throw new HibernateLiteException(
                    "IN / NOT IN requires a non-empty collection");
        }
        return in;
    }

    /**
     * Validates that a referenced field exists in the target entity.
     *
     * <p>The metadata registry has already scanned the entity fields.
     * This method performs a simple lookup before JPA is asked to
     * construct a Criteria path.</p>
     *
     * <p>Package-private visibility allows focused unit tests to call
     * this validation method directly.</p>
     *
     * @param fieldName entity field name
     * @param meta      metadata of the target entity
     * @throws HibernateLiteException if the field does not exist
     */
    static void validateField(String fieldName, EntityMeta meta) {
        if (meta.getField(fieldName) == null) {
            throw new HibernateLiteException(
                    "Entity " + meta.getEntityClass().getName()
                            + " has no field: " + fieldName);
        }
    }

}