package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** Base class for every domain failure. */
public class RutaturException extends RuntimeException {

    private final ErrorCode code;

    public RutaturException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RutaturException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public int getStatus() {
        return code.statusValue();
    }
}
