package com.rutatur.common.util;

import com.rutatur.common.dto.GeoPointDto;

/**
 * The single distance implementation of the platform.
 *
 * <p>Great-circle distance in meters using the Haversine formula with the mean Earth radius
 * 6 371 008.8 m. Identical coordinates short-circuit to exactly {@code 0.0}.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoUtils() {
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == lat2 && lon1 == lon2) {
            return 0.0;
        }
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double sinLat = Math.sin(deltaLat / 2);
        double sinLon = Math.sin(deltaLon / 2);
        double a = sinLat * sinLat + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static double haversineMeters(GeoPointDto a, GeoPointDto b) {
        return haversineMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude());
    }
}
