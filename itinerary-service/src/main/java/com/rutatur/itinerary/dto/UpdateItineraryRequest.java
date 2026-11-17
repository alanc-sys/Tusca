package com.rutatur.itinerary.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.itinerary.dto.SaveItineraryRequest.SaveItineraryStopRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Solicitud de actualización parcial (PATCH) para un itinerario guardado.
 * Los campos nulos se ignoran y conservan su valor actual.
 */
public record UpdateItineraryRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre del itinerario no puede estar vacío")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
        String name,

        GeoPointDto startPoint,

        TransportMode transportMode,

        @Min(value = 1, message = "totalDurationMinutes debe ser positivo")
        Integer totalDurationMinutes,

        @Size(min = 1, message = "El itinerario debe contener al menos un POI")
        List<@NotNull(message = "Cada parada es obligatoria") @Valid SaveItineraryStopRequest> stops
) {
    public boolean hasAnyField() {
        return name != null
                || startPoint != null
                || transportMode != null
                || totalDurationMinutes != null
                || stops != null;
    }
}
