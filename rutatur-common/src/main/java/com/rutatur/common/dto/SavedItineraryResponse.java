package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** Result of claiming a draft. {@code id} equals {@code itineraryId} by design. */
public record SavedItineraryResponse(
        UUID id,
        String userId,
        UUID itineraryId,
        Instant createdAt) {
}
