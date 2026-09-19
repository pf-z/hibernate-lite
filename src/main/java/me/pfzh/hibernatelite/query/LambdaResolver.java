package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.exception.HibernateLiteException;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * Resolves entity field names from method references.
 *
 * <p>{@code User::getName}   → {@code "name"}</p>
 * <p>{@code User::isActive}  → {@code "active"}</p>
 *
 * <p><b>How it works:</b> the JVM compiles a lambda expression into a
 * synthetic class with a {@code writeReplace} method that returns a
 * {@link SerializedLambda}. The implementation method name is then
 * extracted and its {@code get} / {@code is} prefix is stripped using
 * {@link Introspector#decapitalize(String)} to correctly handle
 * consecutive uppercase letters (e.g. {@code getURL} → {@code URL},
 * not {@code uRL}).</p>
 *
 * <p>This class is package-private and stateless.</p>

 */
final class LambdaResolver {

    private LambdaResolver() {}

    /**
     * Resolves a field name from a getter-style method reference.
     *
     * <p>This method is generic (not using wildcards) so that the
     * compiler can correctly infer the {@code SFunction<T, R>} type
     * from a lambda expression.</p>
     */
    static <T, R> String resolve(SFunction<T, R> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("Method reference cannot be null");
        }

        SerializedLambda lambda = extractLambda(fn);
        String methodName = lambda.getImplMethodName();
        return stripPrefix(methodName, fn);
    }

    private static <T, R> SerializedLambda extractLambda(SFunction<T, R> fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object result = writeReplace.invoke(fn);
            if (!(result instanceof SerializedLambda lambda)) {
                throw new HibernateLiteException(
                        "Method reference is not a lambda expression");
            }
            return lambda;
        } catch (NoSuchMethodException e) {
            throw new HibernateLiteException(
                    "Method reference is not a lambda expression "
                            + "(possibly optimized by the compiler)", e);
        } catch (ReflectiveOperationException e) {
            throw new HibernateLiteException(
                    "Failed to resolve lambda expression", e);
        }
    }

    private static <T, R> String stripPrefix(String methodName, SFunction<T, R> fn) {
        String prefix;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            prefix = "get";
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            prefix = "is";
        } else {
            throw new HibernateLiteException(
                    "Method reference must be getter-style (getXxx / isXxx): "
                            + fn.getClass().getName() + "#" + methodName);
        }
        return Introspector.decapitalize(methodName.substring(prefix.length()));
    }

}