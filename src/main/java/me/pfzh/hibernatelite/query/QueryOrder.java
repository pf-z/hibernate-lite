package me.pfzh.hibernatelite.query;

/**
 * Represents a single ordering clause in a query.
 *
 * <p>The getter method reference is resolved to the entity property name
 * when the ordering clause is created.</p>
 *
 * <p>Instances are immutable.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
final class QueryOrder {

    /** Entity property name used for ordering. */
    private final String fieldName;

    /** Whether the ordering is ascending. */
    private final boolean ascending;

    /**
     * Creates an ordering clause.
     *
     * @param field getter-style method reference
     * @param ascending {@code true} for ascending order,
     *                  {@code false} for descending order
     */
    <T, R> QueryOrder(SFunction<T, R> field, boolean ascending) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }

        /*
         * Resolve the method reference once.
         *
         * For example:
         *
         *     User::getAge → "age"
         */
        this.fieldName = LambdaResolver.resolve(field);
        this.ascending = ascending;
    }

    /**
     * Returns the entity property name used for ordering.
     *
     * @return property name
     */
    String fieldName() {
        return fieldName;
    }

    /**
     * Returns whether this ordering is ascending.
     *
     * @return {@code true} for ascending, {@code false} for descending
     */
    boolean ascending() {
        return ascending;
    }

}
