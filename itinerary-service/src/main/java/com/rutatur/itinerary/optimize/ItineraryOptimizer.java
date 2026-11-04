package com.rutatur.itinerary.optimize;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.ItineraryRequestDto;
import com.rutatur.common.dto.ItineraryResponseDto;
import com.rutatur.common.dto.ItineraryStopDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.BusinessException;
import com.rutatur.common.util.GeoUtils;
import com.rutatur.itinerary.client.RoutingMatrix;
import com.rutatur.itinerary.config.ItineraryProperties;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic greedy optimizer.
 *
 * <p>Candidates are deduplicated, radius-filtered and totally ordered before the routing matrix is
 * requested. The greedy loop visits them in that order and skips any candidate that does not fit the
 * remaining time, which keeps the route feasible without look-ahead.
 */
@Component
public class ItineraryOptimizer {

    private final CandidateScorer scorer;
    private final ItineraryProperties properties;

    public ItineraryOptimizer(CandidateScorer scorer, ItineraryProperties properties) {
        this.scorer = scorer;
        this.properties = properties;
    }

    /** Dedupe, radius filter, score, total order and truncate to the matrix bound. */
    public List<PoiDto> rank(ItineraryRequestDto request, List<PoiDto> candidates) {
        Map<String, PoiDto> unique = new LinkedHashMap<>();
        for (PoiDto candidate : candidates) {
            if (candidate.visitTime() <= 0) {
                continue;
            }
            if (GeoUtils.haversineMeters(request.startPoint(), candidate.location()) > request.radius()) {
                continue;
            }
            unique.putIfAbsent(candidate.id(), candidate);
        }
        List<PoiDto> ordered = new ArrayList<>(unique.values());
        ordered.sort(scorer.comparator(request.preferences()));
        if (ordered.size() > properties.maxCandidates()) {
            return List.copyOf(ordered.subList(0, properties.maxCandidates()));
        }
        return List.copyOf(ordered);
    }

    public ItineraryResponseDto buildRoute(
            UUID itineraryId,
            ItineraryRequestDto request,
            List<PoiDto> orderedCandidates,
            RoutingMatrix matrix) {

        LocalTime startTime = properties.startClock();
        int startMinute = startTime.getHour() * 60 + startTime.getMinute();
        int maxPois = request.maxPoisOrDefault();
        int currentIndex = 0;
        int used = 0;
        int visitTotal = 0;
        int travelTotal = 0;
        List<ItineraryStopDto> stops = new ArrayList<>();

        for (int i = 0; i < orderedCandidates.size() && stops.size() < maxPois; i++) {
            PoiDto candidate = orderedCandidates.get(i);
            int matrixIndex = i + 1;
            int leg = matrix.durationMinutes(currentIndex, matrixIndex);
            if (leg == RoutingMatrix.UNREACHABLE) {
                continue;
            }
            int distance = matrix.distanceMeters(currentIndex, matrixIndex);
            if (distance == RoutingMatrix.UNREACHABLE) {
                continue;
            }
            if (used + leg + candidate.visitTime() > request.availableTime()) {
                continue;
            }

            LocalTime arrival = startTime.plusMinutes(used + leg);
            LocalTime departure = arrival.plusMinutes(candidate.visitTime());
            stops.add(new ItineraryStopDto(
                    stops.size() + 1,
                    candidate,
                    leg,
                    distance,
                    arrival,
                    departure,
                    candidate.visitTime()));

            used += leg + candidate.visitTime();
            visitTotal += candidate.visitTime();
            travelTotal += leg;
            currentIndex = matrixIndex;
        }

        if (stops.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.ITINERARY_NOT_FEASIBLE, "No itinerary fits the available time");
        }

        GeoPointDto startPoint = request.startPoint();
        return new ItineraryResponseDto(
                itineraryId,
                startPoint,
                List.copyOf(stops),
                used,
                visitTotal,
                travelTotal,
                startTime,
                startTime.plusMinutes(used),
                request.transportMode(),
                true);
    }
}
