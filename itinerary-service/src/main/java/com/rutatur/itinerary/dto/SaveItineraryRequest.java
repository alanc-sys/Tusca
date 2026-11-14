package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveItineraryRequest(
        @NotBlank(message = "El nombre del itinerario es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
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
            @Size(max = 64, message = "poiId no puede superar los 64 caracteres")
            String poiId,
            @Min(1) int visitOrder,
            @Min(1) int stayDurationMinutes,
            @Min(0) int travelTimeToNextMinutes,
            @Min(0) double distanceToNextMeters
    ) {
    }
}
