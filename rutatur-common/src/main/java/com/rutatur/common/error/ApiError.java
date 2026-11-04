package com.rutatur.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Uniform error envelope. {@code violations} is present only for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<Violation> violations) {

    public record Violation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(
                Instant.now(), code.statusValue(), reason(code.statusValue()), code.name(), message, path, null);
    }

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError validation(String message, String path, List<Violation> violations) {
        return new ApiError(
                Instant.now(),
                ErrorCode.VALIDATION_FAILED.statusValue(),
                reason(ErrorCode.VALIDATION_FAILED.statusValue()),
                ErrorCode.VALIDATION_FAILED.name(),
                message,
                path,
                violations);
    }

    /** Minimal reason-phrase table so the shared jar does not depend on the servlet stack. */
    public static String reason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 503 -> "Service Unavailable";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
