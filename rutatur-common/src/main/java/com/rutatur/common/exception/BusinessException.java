package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** A business rule rejected a well-formed request; 422. */
public class BusinessException extends RutaturException {

    public BusinessException(ErrorCode code, String message) {
        super(code, message);
    }
}
