package com.rutatur.common.util;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void haversineReturnsZeroForSamePoint() {
        GeoPointDto cordobaCenter = new GeoPointDto(-31.4167, -64.1833);
        assertEquals(0.0, GeoUtils.haversineDistanceMeters(cordobaCenter, cordobaCenter), 0.001);
    }

    @Test
    void haversineCalculatesKnownDistanceBetweenCordobaLandmarks() {
        // Plaza San Martín (-31.4167, -64.1836) a Parque Sarmiento (-31.4285, -64.1795) ~ 1.37 km
        double meters = GeoUtils.haversineDistanceMeters(-31.4167, -64.1836, -31.4285, -64.1795);
        assertTrue(meters > 1200 && meters < 1550, "Expected ~1370m, got " + meters);
    }

    @Test
    void invalidLatitudeThrowsValidationException() {
        RutaturException ex = assertThrows(RutaturException.class, () -> new GeoPointDto(95.0, -64.18));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void invalidLongitudeThrowsValidationException() {
        RutaturException ex = assertThrows(RutaturException.class, () -> new GeoPointDto(-31.41, 185.0));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }
}
