package me.pfzh.hibernatelite.query;

/**
 * A single query condition.
 *
 * <p>Instances are immutable. The field name is resolved eagerly at
 * construction time so repeated execution of the same query does not
 * re-resolve the method reference.</p>
 *
 * <p>Construction-time validation catches common mistakes early:
 * <ul>
 *     <li>{@code LIKE} requires a {@link String} value.</li>
 *     <li>{@code IN} / {@code NOT_IN} require an {@link Iterable}.</li>
 *     <li>{@code BETWEEN} requires an {@code Object[]} of length 2.</li>
 *     <li>{@code IS_NULL} / {@code IS_NOT_NULL} take no value.</li>
 *     <li>All other operators require a non-null value.</li>
 * </ul>
 * </p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryCondition {

    private final String fieldName;
    private final Operator operator;
    private final Object value;

    /**
     * Creates a query condition.
     *
     * @param field    getter-style method reference; must not be {@code null}
     * @param operator query operator; must not be {@code null}
     * @param value    value bound to the operator; validation depends on
     *                 {@code operator}
     */
    <T, R> QueryCondition(SFunction<T, R> field, Operator operator, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        if (operator == null) {
            throw new IllegalArgumentException("operator cannot be null");
        }

        this.fieldName = LambdaResolver.resolve(field);
        this.operator = operator;
        this.value = value;

        validate();
    }

    String fieldName() {
        return fieldName;
    }

    Operator operator() {
        return operator;
    }

    Object value() {
        return value;
    }

    // ==================== 构造时校验 ====================

    private void validate() {
        switch (operator) {
            case IS_NULL, IS_NOT_NULL -> {
                // no value expected
            }
            case LIKE -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "LIKE requires a String value, got: " + describe(value));
                }
            }
            case IN, NOT_IN -> {
                if (!(value instanceof Iterable)) {
                    throw new IllegalArgumentException(
                            operator + " requires an Iterable value, got: " + describe(value));
                }
            }
            case BETWEEN -> {
                if (!(value instanceof Object[] arr) || arr.length != 2) {
                    throw new IllegalArgumentException(
                            "BETWEEN requires an Object[] of length 2, got: " + describe(value));
                }
            }
            default -> {
                if (value == null) {
                    throw new IllegalArgumentException(
                            operator + " requires a non-null value "
                                    + "(use isNull() to test for null)");
                }
            }
        }
    }

    private static String describe(Object v) {
        if (v == null) return "null";
        return v.getClass().getSimpleName() + "[" + v + "]";
    }

}