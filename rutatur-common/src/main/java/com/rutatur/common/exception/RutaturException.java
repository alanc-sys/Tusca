package com.rutatur.common.exception;

import java.util.List;

/**
 * Excepción base de negocio y plataforma que porta un {@link ErrorCode} estandarizado.
 */
public class RutaturException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public RutaturException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), List.of());
    }

    public RutaturException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public RutaturException(ErrorCode errorCode, String message, List<String> details) {
        super(message == null || message.isBlank() ? errorCode.getDefaultMessage() : message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return details;
    }
}
