package com.trading.exception;

public class InvalidReferenceException extends RuntimeException {

    private static final long serialVersionUID = 3177690324341164254L;

	public InvalidReferenceException(String message) {
        super(message);
    }
}
