package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.PoiDto;

public record ItineraryStopDto(
        int visitOrder,
        PoiDto poi,
        int stayDurationMinutes,
        int travelTimeToNextMinutes,
        double distanceToNextMeters
) {
}
