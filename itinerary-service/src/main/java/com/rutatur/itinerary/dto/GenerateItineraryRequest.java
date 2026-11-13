package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateItineraryRequest(
        @NotNull(message = "startPoint es obligatorio")
        GeoPointDto startPoint,

        @Min(value = 1, message = "availableTimeMinutes debe ser mayor a 0")
        @Max(value = 1440, message = "availableTimeMinutes no puede superar 1440 minutos")
        int availableTimeMinutes,

        Integer radiusMeters,
        TransportMode transportMode,
        List<String> categories
) {
    public int resolvedRadiusMeters() {
        return radiusMeters == null ? 2000 : radiusMeters;
    }

    public TransportMode resolvedTransportMode() {
        return transportMode == null ? TransportMode.WALKING : transportMode;
    }
}
