package com.rutatur.common.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Claim request for a generated draft. */
public record SaveItineraryRequest(@NotNull UUID itineraryId) {
}
