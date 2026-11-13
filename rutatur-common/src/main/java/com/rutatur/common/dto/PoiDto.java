package com.rutatur.common.dto;

/**
 * Contrato compartido de Punto de Interés (POI) entre poi-service y los servicios consumidores.
 */
public record PoiDto(
        String id,
        String name,
        String category,
        GeoPointDto location,
        String address,
        int estimatedStayMinutes
) {
}
