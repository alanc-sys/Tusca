package com.rutatur.common.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request a match against another user's shared route. */
public record MatchRequestDto(@NotNull UUID targetRouteId) {
}
