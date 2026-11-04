package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** History list row. */
public record SavedItinerarySummary(
        UUID id,
        UUID itineraryId,
        GeoPointDto startPoint,
        int totalTime,
        String routeSummary,
        Instant createdAt) {
}
