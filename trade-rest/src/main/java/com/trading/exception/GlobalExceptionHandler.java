package com.trading.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import com.trading.repository.InvalidPaginationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation failed.",
                request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation failed.",
                request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", "Request body is invalid.",
                request, Map.of());
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidReference(
            InvalidReferenceException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid reference", exception.getMessage(),
                request, Map.of());
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiError> handleInvalidPagination(
            InvalidPaginationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid pagination", exception.getMessage(),
                request, Map.of());
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<ApiError> handleMissingResource(
            EmptyResultDataAccessException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Not found", "Requested resource was not found.",
                request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConstraint(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", "Request violates a data constraint.",
                request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        logger.error("Unexpected REST API error for {} {}", request.getMethod(), request.getRequestURI(),
                exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", request, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String error, String message,
                                           HttpServletRequest request,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), error, message, request.getRequestURI(), fieldErrors));
    }
}
