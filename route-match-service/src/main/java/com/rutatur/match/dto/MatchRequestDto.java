package com.rutatur.match.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatchRequestDto(
        @NotNull(message = "myRouteId es obligatorio")
        UUID myRouteId,
        @NotNull(message = "targetRouteId es obligatorio")
        UUID targetRouteId
) {
}
