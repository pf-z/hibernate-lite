package me.pfzh.hibernatelite.query;

/**
 * Supported query operators.
 *
 * <p>Each operator is translated into a corresponding JPA Criteria
 * predicate by {@code QueryBuilder}.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
enum Operator {

    /** Equal to ({@code =}). */
    EQ,

    /** Not equal to ({@code <>}). */
    NE,

    /** Greater than ({@code >}). */
    GT,

    /** Greater than or equal to ({@code >=}). */
    GE,

    /** Less than ({@code <}). */
    LT,

    /** Less than or equal to ({@code <=}). */
    LE,

    /** Pattern matching ({@code LIKE}). */
    LIKE,

    /** Value is contained in a collection ({@code IN}). */
    IN,

    /** Value is not contained in a collection ({@code NOT IN}). */
    NOT_IN,

    /** Value is null ({@code IS NULL}). */
    IS_NULL,

    /** Value is not null ({@code IS NOT NULL}). */
    IS_NOT_NULL,

    /** Value falls within a range ({@code BETWEEN}). */
    BETWEEN

}