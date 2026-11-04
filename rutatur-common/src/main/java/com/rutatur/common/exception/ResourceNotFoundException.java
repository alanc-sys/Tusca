package com.rutatur.common.exception;

import com.rutatur.common.error.ErrorCode;

/** Missing resource; the code distinguishes itinerary, share and match lookups. */
public class ResourceNotFoundException extends RutaturException {

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
