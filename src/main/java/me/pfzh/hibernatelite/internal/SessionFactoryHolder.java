package me.pfzh.hibernatelite.internal;

import org.hibernate.SessionFactory;

/**
 * Holds the global SessionFactory instance.
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class SessionFactoryHolder implements AutoCloseable {

    private final SessionFactory factory;

    public SessionFactoryHolder(SessionFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("SessionFactory cannot be null");
        }
        this.factory = factory;
    }

    /**
     * Returns the SessionFactory.
     */
    public SessionFactory get() {
        return factory;
    }

    /**
     * Closes the SessionFactory.
     */
    @Override
    public void close() {
        if (!factory.isClosed()) {
            factory.close();
        }
    }

}
