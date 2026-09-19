package me.pfzh.hibernatelite.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Escape hatch for advanced queries expressed directly with the
 * JPA Criteria API.
 *
 * <p>Used together with the Lambda DSL, all predicates are combined
 * with AND:</p>
 *
 * <pre>{@code
 * db.query(User.class)
 *   .eq(User::getStatus, "ACTIVE")
 *   .where((cb, root) -> cb.like(root.get("name"), "Al%"))
 *   .list();
 * }</pre>
 *
 * <p><b>Note:</b> when using this escape hatch, field names are plain
 * strings and are <b>not</b> compile-time checked.</p>
 *
 * @param <T> entity type
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
@FunctionalInterface
public interface QuerySpec<T> {

    /**
     * Builds a JPA Criteria {@link Predicate} from the given builder
     * and root.
     *
     * @param cb   criteria builder
     * @param root query root
     * @return a predicate, or {@code null} to indicate no restriction
     */
    Predicate toPredicate(CriteriaBuilder cb, Root<T> root);
}