package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** Authenticated caller is not allowed to perform the action; 403. */
public class ForbiddenActionException extends RutaturException {

    public ForbiddenActionException(ErrorCode code, String message) {
        super(code, message);
    }
}
