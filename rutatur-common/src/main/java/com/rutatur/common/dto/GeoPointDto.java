package com.rutatur.common.dto;

import com.rutatur.common.util.GeoUtils;

/**
 * Coordenada geográfica validada (latitud y longitud en grados decimales).
 */
public record GeoPointDto(double latitude, double longitude) {

    public GeoPointDto {
        GeoUtils.validateCoordinates(latitude, longitude);
    }
}
