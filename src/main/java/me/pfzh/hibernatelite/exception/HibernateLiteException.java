package me.pfzh.hibernatelite.exception;

import java.io.Serial;

/**
 * Base exception type for Hibernate-Lite.
 *
 * <p>
 * All exceptions thrown by Hibernate-Lite are wrapped into this type,
 * allowing users to handle library errors through a single exception class.
 * </p>
 *
 * <p>
 * Extends {@link RuntimeException}, therefore users are not forced
 * to catch or declare checked exceptions.
 * </p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public class HibernateLiteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception without message or cause.
     */
    public HibernateLiteException(){}

    /**
     * Creates an exception with a custom message.
     *
     * @param message error description
     */
    public HibernateLiteException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and original cause.
     *
     * @param message error description
     * @param cause original exception
     */
    public HibernateLiteException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception caused by another exception.
     *
     * @param cause original exception
     */
    public HibernateLiteException(Throwable cause) {
        super(cause);
    }

}
