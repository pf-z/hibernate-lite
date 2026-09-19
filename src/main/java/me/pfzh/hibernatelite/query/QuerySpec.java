package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Escape hatch for advanced queries using the JPA Criteria API.
 *
 * <p>Used together with the Lambda query DSL, all predicates are
 * combined with {@code AND}.</p>
 *
 * <p>For example:</p>
 *
 * <pre>{@code
 * db.query(User.class)
 *     .eq(User::getStatus, "ACTIVE")
 *     .where((cb, root) -> cb.like(root.get("name"), "Al%"))
 *     .list();
 * }</pre>
 *
 * <p>The Lambda DSL is type-safe, while this escape hatch allows
 * direct access to the JPA Criteria API for query conditions that
 * are not directly supported by the DSL.</p>
 *
 * <p><b>Note:</b> field names used through {@code root.get(...)} are
 * plain strings and therefore are not checked by the Java compiler.</p>
 *
 * @param <T> entity type
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
@FunctionalInterface
public interface QuerySpec<T> {

    /**
     * Builds a JPA Criteria predicate.
     *
     * <p>The returned predicate is combined with conditions created
     * through the Lambda DSL by {@code AND}.</p>
     *
     * <p>Returning {@code null} means that this specification does
     * not add any additional restriction.</p>
     *
     * @param cb JPA Criteria builder used to construct predicates
     * @param root query root representing the entity being queried
     * @return a predicate, or {@code null} if no restriction is required
     */
    Predicate toPredicate(CriteriaBuilder cb, Root<T> root);

}