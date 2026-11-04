package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** External provider failed or timed out; 503. */
public class UpstreamServiceException extends RutaturException {

    public UpstreamServiceException(ErrorCode code, String message) {
        super(code, message);
    }

    public UpstreamServiceException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
