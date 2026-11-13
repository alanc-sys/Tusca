package com.rutatur.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rutatur.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Formato estándar de respuesta de error para el API Gateway y todos los microservicios.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<String> details
) {

    public static ApiError of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, path, List.of());
    }

    public static ApiError of(ErrorCode errorCode, String message, String path, List<String> details) {
        String resolvedMessage = (message == null || message.isBlank())
                ? errorCode.getDefaultMessage()
                : message;
        return new ApiError(
                Instant.now(),
                errorCode.getHttpStatus(),
                errorCode.name(),
                resolvedMessage,
                path,
                details == null ? List.of() : List.copyOf(details)
        );
    }
}
