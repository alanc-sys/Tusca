package com.rutatur.common.enums;

/**
 * Modos de traslado soportados para la generación de itinerarios y consultas OSRM.
 */
public enum TransportMode {
    WALKING("foot"),
    DRIVING("car"),
    CYCLING("bike");

    private final String osrmProfile;

    TransportMode(String osrmProfile) {
        this.osrmProfile = osrmProfile;
    }

    public String getOsrmProfile() {
        return osrmProfile;
    }
}
