package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveItineraryRequest(
        @NotBlank(message = "El nombre del itinerario es obligatorio")
        String name,

        @NotNull(message = "startPoint es obligatorio")
        GeoPointDto startPoint,

        @NotNull(message = "transportMode es obligatorio")
        TransportMode transportMode,

        @Min(value = 1, message = "totalDurationMinutes debe ser positivo")
        int totalDurationMinutes,

        @NotEmpty(message = "El itinerario debe contener al menos un POI")
        @Valid
        List<SaveItineraryStopRequest> stops
) {
    public record SaveItineraryStopRequest(
            @NotBlank(message = "poiId es obligatorio")
            String poiId,
            @Min(1) int visitOrder,
            @Min(1) int stayDurationMinutes,
            @Min(0) int travelTimeToNextMinutes,
            @Min(0) double distanceToNextMeters
    ) {
    }
}
