/**
 * Hibernate-Lite — a lightweight persistence layer built on top of
 * Hibernate ORM.
 *
 * <p>This package contains the public entry point and the facade
 * interface exposed to user code:</p>
 * <ul>
 *     <li>{@link me.pfzh.hibernatelite.HibernateLite} — bootstrap
 *     entry point and builder.</li>
 *     <li>{@link me.pfzh.hibernatelite.DataStore} — the only facade
 *     interface users need to depend on.</li>
 * </ul>
 *
 * <p>All other packages are internal implementation details and may
 * change without notice. User code should depend only on
 * {@code DataStore} and the types referenced by its methods.</p>
 */
package me.pfzh.hibernatelite;