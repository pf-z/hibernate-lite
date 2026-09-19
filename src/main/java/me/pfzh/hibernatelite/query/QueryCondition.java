package me.pfzh.hibernatelite.query;

/**
 * Represents a single condition in a query.
 *
 * <p>A {@code QueryCondition} is the internal representation of one
 * condition added through the {@link LambdaQuery} DSL. For example:</p>
 *
 * <pre>
 * .eq(User::getAge, 18)
 * </pre>
 *
 * <p>is converted into a condition containing:</p>
 * <ul>
 *     <li>field name: {@code "age"}</li>
 *     <li>operator: {@link Operator#EQ}</li>
 *     <li>value: {@code 18}</li>
 * </ul>
 *
 * <p>The condition is intentionally independent of JPA Criteria API.
 * {@link QueryBuilder} is responsible for translating it into a
 * Criteria {@code Predicate} during query execution.</p>
 *
 * <p>Instances are immutable. The field name is resolved eagerly during
 * construction so the method reference only needs to be resolved once,
 * rather than every time the condition is later translated into a query.</p>
 *
 * <p>Operator-specific validation is also performed during construction.
 * This allows invalid query definitions to fail as early as possible,
 * before a database query is executed.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryCondition {

    /** Entity property name resolved from the getter method reference. */
    private final String fieldName;

    /** Operator applied to the field. */
    private final Operator operator;

    /** Value associated with the operator. */
    private final Object value;

    /**
     * Creates a query condition.
     *
     * <p>The field method reference is resolved immediately and the
     * resulting property name is stored. This keeps this class independent
     * from the later Criteria API construction process.</p>
     *
     * @param field getter-style method reference, such as {@code User::getAge}
     * @param operator query operator
     * @param value value associated with the operator
     * @throws IllegalArgumentException if {@code field} or {@code operator}
     *                                  is {@code null}, or if the value is
     *                                  incompatible with the operator
     */
    <T, R> QueryCondition(SFunction<T, R> field, Operator operator, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        if (operator == null) {
            throw new IllegalArgumentException("operator cannot be null");
        }

        /*
         * Resolve the method reference once and store the property name.
         *
         * For example:
         *
         *     User::getAge -> "age"
         *
         * The QueryBuilder can then work with a simple field name without
         * needing to understand SerializedLambda or reflection.
         */
        this.fieldName = LambdaResolver.resolve(field);

        this.operator = operator;
        this.value = value;

        /*
         * Validate the combination of operator and value immediately.
         * Invalid conditions should fail when the query is built rather
         * than much later during Criteria construction or SQL execution.
         */
        validate();
    }

    /**
     * Returns the entity property name used by this condition.
     *
     * @return resolved property name
     */
    String fieldName() {
        return fieldName;
    }

    /**
     * Returns the operator of this condition.
     *
     * @return query operator
     */
    Operator operator() {
        return operator;
    }

    /**
     * Returns the value associated with this condition.
     *
     * @return condition value, or {@code null} for null-check operators
     */
    Object value() {
        return value;
    }

    // ==================== Construction-time validation ====================

    /**
     * Validates that the value is compatible with the selected operator.
     *
     * <p>This validation intentionally checks only the structural contract
     * of the operator. It does not try to determine whether the Java value
     * is type-compatible with the actual entity property; that validation
     * is left to JPA/Hibernate.</p>
     */
    private void validate() {
        switch (operator) {

            /*
             * IS NULL and IS NOT NULL do not use a comparison value.
             * The null passed by LambdaQuery is only a placeholder and
             * is not used when building the Criteria predicate.
             */
            case IS_NULL, IS_NOT_NULL -> {
                // no value expected
            }


            /*
             * LIKE represents a string pattern match, so its value must
             * be a String.
             */
            case LIKE -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "LIKE requires a String value, got: " + describe(value));
                }
            }

            /*
             * IN and NOT IN require multiple candidate values.
             *
             * Iterable is used instead of Collection so callers can also
             * provide other iterable containers without coupling the DSL
             * to a specific collection type.
             */
            case IN, NOT_IN -> {
                if (!(value instanceof Iterable)) {
                    throw new IllegalArgumentException(
                            operator + " requires an Iterable value, got: " + describe(value));
                }
            }

            /*
             * BETWEEN is represented internally by exactly two values:
             *
             *     new Object[]{low, high}
             *
             * The actual conversion into a CriteriaBuilder.between(...)
             * predicate is handled later by QueryBuilder.
             */
            case BETWEEN -> {
                if (!(value instanceof Object[] arr) || arr.length != 2) {
                    throw new IllegalArgumentException(
                            "BETWEEN requires an Object[] of length 2, got: " + describe(value));
                }
            }

            /*
             * All remaining comparison operators require an actual value.
             *
             * For example:
             *
             *     eq(User::getAge, null)
             *
             * is rejected because null equality should be expressed as:
             *
             *     isNull(User::getAge)
             *
             * This keeps SQL NULL semantics explicit instead of silently
             * turning EQ into IS NULL.
             */
            default -> {
                if (value == null) {
                    throw new IllegalArgumentException(
                            operator + " requires a non-null value "
                                    + "(use isNull() to test for null)");
                }
            }
        }
    }

    /**
     * Creates a compact description of a value for validation errors.
     *
     * <p>The helper is used only for diagnostics; it does not participate
     * in query construction.</p>
     *
     * @param v value to describe
     * @return human-readable value description
     */
    private static String describe(Object v) {
        if (v == null) return "null";
        return v.getClass().getSimpleName() + "[" + v + "]";
    }

}