package com.rutatur.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** One ordered stop of a generated itinerary. */
public record ItineraryStopDto(
        @Min(1) int stopOrder,
        @NotNull @Valid PoiDto poi,
        @Min(0) int travelTimeFromPrevious,
        @Min(0) int distanceFromPrevious,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime departureTime,
        @Min(1) int visitDuration) {
}
