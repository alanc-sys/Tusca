package com.rutatur.match.service;

import com.rutatur.common.util.GeoUtils;
import com.rutatur.match.entity.SharedRouteEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Calcula la similitud entre dos rutas compartidas combinando Jaccard de POIs,
 * proximidad geográfica de inicio y compatibilidad de modo de transporte (RN-07).
 */
@Component
public class SimilarityCalculator {

    public static final double MIN_SIMILARITY_THRESHOLD = 0.35;

    private static final double POI_JACCARD_WEIGHT = 0.55;
    private static final double PROXIMITY_WEIGHT = 0.30;
    private static final double TRANSPORT_WEIGHT = 0.15;
    private static final double MAX_PROXIMITY_METERS = 5000.0;

    public double calculate(SharedRouteEntity routeA, SharedRouteEntity routeB) {
        double jaccard = calculateJaccard(routeA.getPoiIds(), routeB.getPoiIds());

        double distanceMeters = GeoUtils.haversineDistanceMeters(
                routeA.getStartLatitude(), routeA.getStartLongitude(),
                routeB.getStartLatitude(), routeB.getStartLongitude());
        double proximity = Math.max(0.0, 1.0 - (distanceMeters / MAX_PROXIMITY_METERS));

        double transport = routeA.getTransportMode() == routeB.getTransportMode() ? 1.0 : 0.3;

        double rawScore = (jaccard * POI_JACCARD_WEIGHT)
                + (proximity * PROXIMITY_WEIGHT)
                + (transport * TRANSPORT_WEIGHT);

        return Math.round(rawScore * 10000.0) / 10000.0;
    }

    public Set<String> intersectPois(SharedRouteEntity routeA, SharedRouteEntity routeB) {
        Set<String> intersection = new HashSet<>(routeA.getPoiIds());
        intersection.retainAll(routeB.getPoiIds());
        return intersection;
    }

    private double calculateJaccard(Set<String> poisA, Set<String> poisB) {
        if (poisA.isEmpty() && poisB.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(poisA);
        union.addAll(poisB);
        if (union.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(poisA);
        intersection.retainAll(poisB);
        return (double) intersection.size() / union.size();
    }
}
