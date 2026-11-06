package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** An owned itinerary plus its share state. */
public record ShareableItineraryDto(
        UUID itineraryId,
        GeoPointDto startPoint,
        int totalTime,
        boolean shared,
        UUID shareId,
        Instant expiresAt) {
}
