package me.pfzh.hibernatelite.query;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A serializable function used by the Lambda query DSL to reference
 * entity properties in a type-safe way.
 *
 * <p>For example:</p>
 *
 * <pre>
 * User::getName
 * User::getAge
 * User::isActive
 * </pre>
 *
 * <p>{@code SFunction<T, R>} is a specialized {@link Function} that
 * accepts an entity of type {@code T} and returns a property of type
 * {@code R}.</p>
 *
 * <p>The {@link Serializable} marker is important because
 * {@link LambdaResolver} uses the JVM's {@code SerializedLambda}
 * mechanism to inspect the method reference and recover its
 * implementation method name.</p>
 *
 * <p>For example:</p>
 *
 * <pre>
 * User::getName
 *      ↓
 * SerializedLambda
 *      ↓
 * "getName"
 *      ↓
 * "name"
 * </pre>
 *
 * <p>Therefore, {@code SFunction} allows the query DSL to provide
 * type-safe field references such as:</p>
 *
 * <pre>
 * db.query(User.class)
 *     .eq(User::getName, "Tom")
 *     .orderByDesc(User::getAge);
 * </pre>
 *
 * <p>Only getter-style method references are supported. The referenced
 * method should follow the JavaBeans getter convention, such as
 * {@code getXxx()} or {@code isXxx()}.</p>
 *
 * @param <T> entity type accepted by the function
 * @param <R> property type returned by the function
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {

}