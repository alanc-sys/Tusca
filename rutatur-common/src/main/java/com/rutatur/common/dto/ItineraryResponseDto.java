package com.rutatur.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Generated itinerary. Time invariants are guaranteed by the optimizer. */
public record ItineraryResponseDto(
        @NotNull UUID itineraryId,
        @NotNull @Valid GeoPointDto startPoint,
        @NotNull List<@Valid ItineraryStopDto> route,
        int totalTime,
        int totalVisitTime,
        int totalTravelTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull TransportMode transportMode,
        boolean feasible) {
}
