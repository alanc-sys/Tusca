package com.rutatur.common.dto;

import com.rutatur.common.enums.Category;
import com.rutatur.common.enums.TransportMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Matching-relevant snapshot served to route-match-service. Contact-free by construction. */
public record InternalItineraryDto(
        UUID itineraryId,
        String ownerUserId,
        GeoPointDto startPoint,
        List<Category> preferences,
        int availableTime,
        TransportMode transportMode,
        int totalTime,
        int totalVisitTime,
        int totalTravelTime,
        List<String> poiIds,
        String routeSummary,
        Instant createdAt) {
}
