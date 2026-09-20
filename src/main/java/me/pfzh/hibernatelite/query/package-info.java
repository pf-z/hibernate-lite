/**
 * Type-safe query DSL.
 *
 * <p>This package provides the fluent query API used through
 * {@code DataStore.query(Class)}. It is built on top of the JPA
 * Criteria API and supports:</p>
 * <ul>
 *     <li>A chainable {@code LambdaQuery} with operators such as
 *     {@code eq}, {@code ne}, {@code gt}, {@code like},
 *     {@code in}, {@code between}, and {@code isNull}.</li>
 *     <li>Method-reference field resolution via {@code SFunction}
 *     and {@code LambdaResolver}, so that field names are checked at
 *     compile time and validated against {@code EntityMeta} at
 *     runtime.</li>
 *     <li>Multi-field ordering, aggregates ({@code count},
 *     {@code exists}, {@code one}), and conditional delete.</li>
 *     <li>An escape hatch, {@code QuerySpec}, for raw JPA Criteria
 *     predicates when the DSL does not cover a case.</li>
 * </ul>
 *
 * <p>{@code LambdaQuery} is not thread-safe. Each query should be
 * built and executed within a single thread.</p>
 */
package me.pfzh.hibernatelite.query;