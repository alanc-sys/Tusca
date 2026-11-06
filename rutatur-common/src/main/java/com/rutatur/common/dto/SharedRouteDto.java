package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** Opt-in publication of an itinerary. Contains no contact data. */
public record SharedRouteDto(
        UUID id,
        UUID itineraryId,
        boolean active,
        Instant createdAt,
        Instant expiresAt) {
}
