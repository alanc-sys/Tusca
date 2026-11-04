package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** Owner-scoped itinerary summary for the shareable listing. */
public record InternalItinerarySummary(
        UUID itineraryId,
        String ownerUserId,
        GeoPointDto startPoint,
        int totalTime,
        String routeSummary,
        Instant createdAt) {
}
