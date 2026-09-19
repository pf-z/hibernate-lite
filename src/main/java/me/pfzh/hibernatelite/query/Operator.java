package me.pfzh.hibernatelite.query;

/**
 * Supported query operators.
 *
 * <p>Each operator maps to a JPA Criteria predicate in
 * {@code QueryBuilder}.</p>
 */
enum Operator {

    /** {@code =} */
    EQ,
    /** {@code <>} */
    NE,
    /** {@code >} */
    GT,
    /** {@code >=} */
    GE,
    /** {@code <} */
    LT,
    /** {@code <=} */
    LE,
    /** {@code LIKE} */
    LIKE,
    /** {@code IN} */
    IN,
    /** {@code NOT IN} */
    NOT_IN,
    /** {@code IS NULL} */
    IS_NULL,
    /** {@code IS NOT NULL} */
    IS_NOT_NULL,
    /** {@code BETWEEN} */
    BETWEEN

}