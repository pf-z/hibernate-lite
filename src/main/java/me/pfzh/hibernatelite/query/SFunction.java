package me.pfzh.hibernatelite.query;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Serializable {@link Function} used for resolving entity field names
 * from method references.
 *
 * <p>Java compiles lambda expressions into synthetic classes that
 * implement {@code writeReplace} to produce a
 * {@link java.lang.invoke.SerializedLambda}. By making the functional
 * interface {@link Serializable}, the runtime can recover the
 * implementation method name (e.g. {@code getName}) and derive the
 * corresponding field name (e.g. {@code name}).</p>
 *
 * <p><b>Constraint:</b> only getter-style method references are
 * supported, such as {@code User::getName} or {@code User::isActive}.</p>
 *
 * @param <T> the entity type
 * @param <R> the field type
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}