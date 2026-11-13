package com.rutatur.common.util;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;

/**
 * Utilidades geográficas compartidas para validación de coordenadas y cálculo de distancias.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {
    }

    public static boolean isValidLatitude(double latitude) {
        return !Double.isNaN(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    public static boolean isValidLongitude(double longitude) {
        return !Double.isNaN(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    public static void validateCoordinates(double latitude, double longitude) {
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            throw new RutaturException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format("Coordenadas inválidas: lat=%s, lon=%s", latitude, longitude)
            );
        }
    }

    public static double haversineDistanceMeters(GeoPointDto origin, GeoPointDto target) {
        if (origin == null || target == null) {
            throw new RutaturException(ErrorCode.VALIDATION_ERROR, "Las coordenadas no pueden ser nulas");
        }
        return haversineDistanceMeters(
                origin.latitude(), origin.longitude(),
                target.latitude(), target.longitude()
        );
    }

    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        validateCoordinates(lat1, lon1);
        validateCoordinates(lat2, lon2);

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
