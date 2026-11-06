package com.rutatur.common.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Opt-in share request. */
public record ShareRouteRequest(@NotNull UUID itineraryId) {
}
