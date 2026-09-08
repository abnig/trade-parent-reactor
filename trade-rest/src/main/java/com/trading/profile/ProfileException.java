package com.trading.profile;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ProfileException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> fieldErrors;
    public ProfileException(HttpStatus status, String field, String message) {
        super(message); this.status = status;
        this.fieldErrors = field == null ? Map.of() : Map.of(field, message);
    }
    public HttpStatus status() { return status; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
