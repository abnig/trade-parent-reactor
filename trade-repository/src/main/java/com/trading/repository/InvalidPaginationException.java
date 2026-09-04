package com.trading.repository;

public class InvalidPaginationException extends RuntimeException {

    private static final long serialVersionUID = 464988549975574467L;

	public InvalidPaginationException(String message) {
        super(message);
    }
}
