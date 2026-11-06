package com.rutatur.common.security;

/** Shared header name for service-to-service authentication. */
public final class InternalHeaders {

    public static final String INTERNAL_TOKEN = "X-Internal-Token";
    public static final String INTERNAL_PATH_PREFIX = "/internal/";

    private InternalHeaders() {
    }
}
