package com.rutatur.match.web;

import com.rutatur.common.error.ApiError;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps shared error codes to HTTP without leaking upstream details. */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(RutaturException.class)
    public ResponseEntity<ApiError> handleDomain(RutaturException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::violation)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", request.getRequestURI(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.Violation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.Violation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", request.getRequestURI(), violations));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Malformed request", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ErrorCode.ACCESS_DENIED, "Access denied", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "Internal error", request.getRequestURI()));
    }

    private ApiError.Violation violation(FieldError error) {
        return new ApiError.Violation(error.getField(), error.getDefaultMessage());
    }
}
