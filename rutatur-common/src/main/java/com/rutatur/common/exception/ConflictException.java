package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** State conflict such as an illegal match transition or a second claim; 409. */
public class ConflictException extends RutaturException {

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
