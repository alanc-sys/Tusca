package com.rutatur.match.similarity;

import com.rutatur.common.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Weighted similarity: {@code 0.40*jaccard + 0.25*startProximity + 0.20*categoryOverlap +
 * 0.15*timeSimilarity}, clamped to 0..1 and rounded HALF_UP to four decimals.
 */
@Component
public class SimilarityScorer {

    public static final double WEIGHT_JACCARD = 0.40;
    public static final double WEIGHT_START_PROXIMITY = 0.25;
    public static final double WEIGHT_CATEGORY_OVERLAP = 0.20;
    public static final double WEIGHT_TIME_SIMILARITY = 0.15;
    public static final double PROXIMITY_RANGE_METERS = 5000.0;
    public static final int SCALE = 4;

    public BigDecimal score(RouteProfile a, RouteProfile b) {
        double jaccard = jaccard(a.poiIds(), b.poiIds());
        double startProximity = clamp(
                1.0 - GeoUtils.haversineMeters(a.startPoint(), b.startPoint()) / PROXIMITY_RANGE_METERS,
                0.0,
                1.0);
        double categoryOverlap = jaccard(a.categories(), b.categories());
        double timeSimilarity = timeSimilarity(a.availableTime(), b.availableTime());
        double sum = WEIGHT_JACCARD * jaccard
                + WEIGHT_START_PROXIMITY * startProximity
                + WEIGHT_CATEGORY_OVERLAP * categoryOverlap
                + WEIGHT_TIME_SIMILARITY * timeSimilarity;
        return BigDecimal.valueOf(clamp(sum, 0.0, 1.0)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public List<String> matchedPoiIds(RouteProfile a, RouteProfile b) {
        Set<String> intersection = new HashSet<>(a.poiIds());
        intersection.retainAll(b.poiIds());
        return List.copyOf(new TreeSet<>(intersection));
    }

    static <T> double jaccard(Collection<T> left, Collection<T> right) {
        Set<T> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 0.0;
        }
        Set<T> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return intersection.size() / (double) union.size();
    }

    static double timeSimilarity(int left, int right) {
        int denominator = Math.max(Math.max(left, right), 1);
        return 1.0 - Math.abs(left - right) / (double) denominator;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
