package me.pfzh.hibernatelite.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Represents a page of query results.
 *
 * <p>A page can operate in one of two modes:</p>
 *
 * <ul>
 *     <li>
 *         <b>Total-count mode</b> — the total number of matching elements
 *         is available, allowing the caller to determine the total number
 *         of pages.
 *     </li>
 *     <li>
 *         <b>Slice mode</b> — the total count is not calculated, but whether
 *         a next page exists is determined by fetching one extra record.
 *     </li>
 * </ul>
 *
 * <p>Instances are immutable. The result list is defensively copied when
 * the page is created and cannot be modified through the returned
 * {@link #content()} list.</p>
 *
 * @param <T> the type of elements contained in this page
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
public final class Page<T> {

    /** The result elements contained in this page. */
    private final List<T> content;
    /** Zero-based page number. */
    private final int page;
    /** Requested page size. */
    private final int size;

    /**
     * Whether a next page exists when total count is not available.
     *
     * <p>This field is only used in slice mode. In total-count mode,
     * {@link #hasNext()} derives the result from {@link #totalElements}.</p>
     */
    private final boolean hasNext;

    /**
     * Total number of matching elements.
     *
     * <p>Set to {@code -1} when total count was not requested.</p>
     */
    private final long totalElements;

    /**
     * Whether the total element count is available.
     */
    private final boolean hasTotal;

    /**
     * Creates an immutable page result.
     *
     * <p>This constructor is private because callers should use
     * {@link #of(List, PageRequest, long)} or
     * {@link #slice(List, PageRequest, boolean)} to make the page mode
     * explicit.</p>
     */
    private Page(List<T> content, int page, int size,
                 boolean hasNext, long total, boolean hasTotal) {
        // Make a defensive copy so that external modifications to the
        // original list cannot affect this page.
        this.content = List.copyOf(content);
        this.page = page;
        this.size = size;
        this.hasNext = hasNext;
        this.totalElements = total;
        this.hasTotal = hasTotal;
    }

    /**
     * Creates a page with a computed total element count.
     *
     * <p>This is the normal pagination mode. The caller provides the
     * result content and the total number of elements matching the query.</p>
     *
     * @param content the elements contained in the current page
     * @param request the original pagination request
     * @param total total number of elements matching the query
     * @param <T> element type
     * @return an immutable page containing total-count information
     * @throws IllegalArgumentException if {@code content} or {@code request}
     *         is {@code null}, or if {@code total} is negative
     */
    public static <T> Page<T> of(List<T> content, PageRequest request, long total) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total cannot be negative, got: " + total);
        }

        // hasNext is derived from totalElements in total-count mode.
        // The stored flag is therefore not used in this mode.
        return new Page<>(content, request.page(), request.size(), false, total, true);
    }

    /**
     * Creates a slice without calculating the total element count.
     *
     * <p>Slice mode is useful when counting all matching rows would be
     * unnecessarily expensive. The caller is responsible for determining
     * whether another page exists, typically by fetching {@code size + 1}
     * rows and using the extra row as a marker.</p>
     *
     * @param content the elements contained in the current page
     * @param request the original pagination request
     * @param hasNext whether another page exists
     * @param <T> element type
     * @return an immutable slice result
     * @throws IllegalArgumentException if {@code content} or {@code request}
     *         is {@code null}
     */
    public static <T> Page<T> slice(List<T> content, PageRequest request, boolean hasNext) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        return new Page<>(content, request.page(), request.size(), hasNext, -1, false);
    }

    /**
     * Returns the elements contained in this page.
     *
     * @return an unmodifiable list of page content
     */
    public List<T> content() {
        return content;
    }

    /**
     * Returns the number of elements contained in the current page.
     *
     * @return number of elements in this page
     */
    public int numberOfElements() {
        return content.size();
    }

    /**
     * Returns whether this page contains no elements.
     *
     * @return {@code true} if the page is empty
     */
    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * Returns the zero-based page number.
     *
     * @return current page number
     */
    public int page() {
        return page;
    }

    /**
     * Returns the requested page size.
     *
     * @return page size
     */
    public int size() {
        return size;
    }

    /**
     * Returns the total number of elements matching the query.
     *
     * @return total number of elements
     * @throws IllegalStateException if this page was created in slice mode
     */
    public long totalElements() {
        if (!hasTotal) {
            throw new IllegalStateException(
                    "Total element count is not available. "
                            + "Use PageRequest.of(page, size) to enable it.");
        }
        return totalElements;
    }

    /**
     * Returns the total number of pages.
     *
     * <p>The value is calculated by dividing the total number of elements
     * by the requested page size and rounding up.</p>
     *
     * @return total number of pages
     * @throws IllegalStateException if this page was created in slice mode
     */
    public int totalPages() {
        if (!hasTotal) {
            throw new IllegalStateException("Total page count is not available.");
        }
        return (int) Math.ceil((double) totalElements / size);
    }

    /**
     * Returns whether the total element count is available.
     *
     * @return {@code true} for total-count mode, {@code false} for slice mode
     */
    public boolean hasTotal() {
        return hasTotal;
    }

    /**
     * Returns whether another page exists after the current page.
     *
     * <p>In total-count mode, the result is calculated from the total
     * number of pages. In slice mode, the value determined by the query
     * executor is returned.</p>
     *
     * @return {@code true} if another page exists
     */
    public boolean hasNext() {
        if (hasTotal) {
            return page + 1 < totalPages();
        }
        return hasNext;
    }

    /**
     * Returns whether a previous page exists.
     *
     * @return {@code true} if the current page is not the first page
     */
    public boolean hasPrevious() {
        return page > 0;
    }

    /**
     * Returns whether this is the first page.
     *
     * @return {@code true} if {@link #page()} is zero
     */
    public boolean isFirst() {
        return page == 0;
    }

    /**
     * Returns whether this is the last page.
     *
     * <p>In total-count mode, the result is calculated from the total
     * number of pages. In slice mode, it is the inverse of {@link #hasNext()}.</p>
     *
     * @return {@code true} if no next page exists
     */
    public boolean isLast() {
        if (hasTotal) {
            return page + 1 >= totalPages();
        }
        return !hasNext;
    }

    /**
     * Maps each element in this page to another type.
     *
     * <p>Pagination metadata is preserved. Only the element type and
     * content are transformed.</p>
     *
     * <p>For example:</p>
     *
     * <pre>{@code
     * Page<User> users = ...;
     * Page<UserDTO> result = users.map(UserDTO::from);
     * }</pre>
     *
     * @param mapper function used to transform each element
     * @param <R> target element type
     * @return a new page containing mapped elements
     * @throws IllegalArgumentException if {@code mapper} is {@code null}
     */
    public <R> Page<R> map(Function<? super T, R> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper cannot be null");
        }
        List<R> mapped = content.stream().map(mapper).toList();
        return new Page<R>(mapped, page, size, hasNext, totalElements, hasTotal);
    }

}