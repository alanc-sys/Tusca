package com.rutatur.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rutatur.common.enums.TransportMode;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Stored itinerary snapshot; reads never call external services. */
public record SavedItineraryDetail(
        UUID id,
        String userId,
        UUID itineraryId,
        GeoPointDto startPoint,
        int availableTime,
        TransportMode transportMode,
        int totalTime,
        int totalVisitTime,
        int totalTravelTime,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<ItineraryStopDto> route,
        Instant createdAt) {
}
