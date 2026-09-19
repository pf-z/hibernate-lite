package me.pfzh.hibernatelite.pagination;

/**
 * Describes how a paginated query should be executed.
 *
 * <p>{@code PageRequest} contains only the pagination parameters:
 * the page index, page size, and whether the total element count
 * should be calculated.</p>
 *
 * <p>Instances are immutable. To change the pagination mode,
 * create a new request using methods such as {@link #withoutCount()}.</p>
 *
 * <p>Page indexes are 0-based. For example, page {@code 0} is the
 * first page and page {@code 1} is the second page.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/19
 */
public final class PageRequest {

    /**
     * Maximum number of elements that can be requested in one page.
     *
     * <p>This limit prevents accidentally requesting an excessively
     * large result set, which could cause unnecessary memory usage
     * and database load.</p>
     */
    private static final int MAX_SIZE = 1000;

    /** 0-based page index. */
    private final int page;

    /** Maximum number of elements requested for this page. */
    private final int size;

    /**
     * Whether the total number of matching elements should be calculated.
     *
     * <p>When enabled, the query executor performs an additional
     * {@code COUNT} query so that the resulting {@link Page} can provide
     * the total element count and total number of pages.</p>
     */
    private final boolean countTotal;

    /**
     * Creates an immutable page request after validating its parameters.
     *
     * @param page 0-based page index
     * @param size number of elements per page
     * @param countTotal whether the total element count should be calculated
     */
    private PageRequest(int page, int size, boolean countTotal) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        if (size <= 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size must be in [1, " + MAX_SIZE + "], got: " + size);
        }
        this.page = page;
        this.size = size;
        this.countTotal = countTotal;
    }

    /**
     * Creates a page request for the given page index and page size.
     *
     * <p>Total element count is enabled by default. The resulting
     * {@link Page} can therefore provide information such as
     * {@code totalElements()} and {@code totalPages()}.</p>
     *
     * @param page 0-based page index
     * @param size number of elements per page
     * @return a new page request
     * @throws IllegalArgumentException if {@code page < 0}, or if
     *                                  {@code size} is outside the allowed range
     */
    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, true);
    }

    /**
     * Creates a page request for the first page.
     *
     * <p>This is equivalent to {@code PageRequest.of(0, size)}.</p>
     *
     * @param size number of elements per page
     * @return a request for page 0
     * @throws IllegalArgumentException if {@code size} is outside the allowed range
     */
    public static PageRequest firstPage(int size) {
        return new PageRequest(0, size, true);
    }

    /**
     * Returns a new request with total-count calculation disabled.
     *
     * <p>This is useful when the caller only needs the current page and
     * whether another page exists, such as in infinite-scroll or
     * "load more" scenarios.</p>
     *
     * <p>The current request is not modified because {@code PageRequest}
     * is immutable.</p>
     *
     * @return a new request with the same page and size but without
     *         total-count calculation
     */
    public PageRequest withoutCount() {
        return new PageRequest(page, size, false);
    }

    /**
     * Calculates the zero-based offset of the first element.
     *
     * <p>The offset is calculated as:</p>
     *
     * <pre>
     * offset = page × size
     * </pre>
     *
     * <p>For example, with a page size of 20:</p>
     * <ul>
     *     <li>page 0 → offset 0</li>
     *     <li>page 1 → offset 20</li>
     *     <li>page 2 → offset 40</li>
     * </ul>
     *
     * <p>The returned value can be passed directly to JPA's
     * {@code Query#setFirstResult(int)}.</p>
     *
     * @return the zero-based result offset
     */
    public int offset() {
        return page * size;
    }

    /**
     * Returns the 0-based page index.
     *
     * @return page index
     */
    public int page() {
        return page;
    }

    /**
     * Returns the requested page size.
     *
     * @return maximum number of elements for the page
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether the total element count should be calculated.
     *
     * @return {@code true} if a total-count query should be performed
     */
    public boolean countTotal() {
        return countTotal;
    }

}