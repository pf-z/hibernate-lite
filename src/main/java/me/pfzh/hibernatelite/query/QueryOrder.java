package me.pfzh.hibernatelite.query;

/**
 * A single ordering clause.
 *
 * <p>Instances are immutable. The field name is resolved eagerly at
 * construction time.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryOrder {

    private final String fieldName;
    private final boolean ascending;

    /**
     * Creates an ordering clause.
     *
     * @param field     getter-style method reference; must not be {@code null}
     * @param ascending {@code true} for ascending, {@code false} for descending
     */
    <T, R> QueryOrder(SFunction<T, R> field, boolean ascending) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        this.fieldName = LambdaResolver.resolve(field);
        this.ascending = ascending;
    }

    String fieldName() {
        return fieldName;
    }

    boolean ascending() {
        return ascending;
    }

}
