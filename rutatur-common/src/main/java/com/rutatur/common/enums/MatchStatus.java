package com.rutatur.common.enums;

/**
 * Estados posibles de una solicitud de coincidencia de rutas.
 */
public enum MatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
