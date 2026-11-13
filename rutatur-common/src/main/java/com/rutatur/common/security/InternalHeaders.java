package com.rutatur.common.security;

/**
 * Cabeceras HTTP internas para comunicación servicio a servicio y propagación de identidad.
 */
public final class InternalHeaders {

    public static final String X_INTERNAL_TOKEN = "X-Internal-Token";
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_ROLES = "X-User-Roles";

    private InternalHeaders() {
    }
}
