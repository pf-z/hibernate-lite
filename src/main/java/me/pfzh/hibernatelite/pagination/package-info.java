/**
 * Pagination support.
 *
 * <p>Provides immutable pagination request and result types used by
 * the query API:</p>
 * <ul>
 *     <li>{@code PageRequest} — 0-based page index, size cap, and an
 *     optional {@code withoutCount()} mode for infinite-scroll
 *     scenarios.</li>
 *     <li>{@code Page} — immutable page result with total count
 *     metadata and accurate navigation flags. In slice mode
 *     ({@code withoutCount()}), {@code hasNext} is determined
 *     accurately by fetching one extra row.</li>
 * </ul>
 */
package me.pfzh.hibernatelite.pagination;