/**
 * Programmatic transaction support.
 *
 * <p>Provides a minimal, non-AOP transaction API:</p>
 * <ul>
 *     <li>{@code TransactionManager} — entry point for programmatic
 *     transactions with REQUIRED propagation semantics.</li>
 *     <li>{@code TransactionCallback} — functional interface used
 *     as the transaction body, optionally returning a value.</li>
 * </ul>
 *
 * <p>Transactions are reentrant: nested calls participate in the
 * outermost transaction and only the outer scope commits or rolls
 * back.</p>
 */
package me.pfzh.hibernatelite.transaction;