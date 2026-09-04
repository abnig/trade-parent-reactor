package com.trading.repository;

/**
 * Validated pagination input shared by JDBC repository contracts.
 */
public record PageRequest(long page, int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static PageRequest of(long page, long size) {
        if (page < 0) {
            throw new InvalidPaginationException("Page must not be negative.");
        }
        if (size < 1) {
            throw new InvalidPaginationException("Size must be at least 1.");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException("Size must not exceed " + MAX_PAGE_SIZE + ".");
        }

        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new InvalidPaginationException("Page and size produce an offset that is too large.");
        }

        return new PageRequest(page, (int) size);
    }

    public long offset() {
        return Math.multiplyExact(page, size);
    }
}
