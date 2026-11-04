package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** Malformed or out-of-range input; always 400. */
public class InvalidRequestException extends RutaturException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
