package com.rutatur.match.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareRouteRequest(
        @NotNull(message = "itineraryId es obligatorio")
        UUID itineraryId
) {
}
