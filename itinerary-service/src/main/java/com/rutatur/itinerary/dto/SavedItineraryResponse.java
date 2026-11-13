package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedItineraryResponse(
        UUID id,
        String userId,
        String name,
        int totalDurationMinutes,
        GeoPointDto startPoint,
        TransportMode transportMode,
        Instant createdAt,
        List<SavedStopResponse> stops
) {
    public record SavedStopResponse(
            UUID id,
            String poiId,
            int visitOrder,
            int stayDurationMinutes,
            int travelTimeToNextMinutes,
            double distanceToNextMeters
    ) {
    }
}
