package com.rutatur.common.dto;

import java.time.Instant;
import java.util.UUID;

/** Calificación ya persistida, devuelta por {@code POST /api/routes/matches/{matchId}/rate}. */
public record MatchRatingResponse(
        UUID id,
        UUID matchId,
        UUID raterUserId,
        UUID ratedUserId,
        int score,
        String comment,
        Instant createdAt) {
}
