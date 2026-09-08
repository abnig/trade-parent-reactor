package com.trading.recovery;

import org.springframework.http.HttpStatus;

public class RecoveryException extends RuntimeException {
    private final HttpStatus status;
    public RecoveryException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
