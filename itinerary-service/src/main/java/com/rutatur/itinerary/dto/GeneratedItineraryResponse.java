package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;

import java.util.List;

public record GeneratedItineraryResponse(
        GeoPointDto startPoint,
        TransportMode transportMode,
        int totalDurationMinutes,
        int availableTimeMinutes,
        boolean osrmUsed,
        List<ItineraryStopDto> route
) {
}
