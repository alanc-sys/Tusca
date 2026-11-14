package com.rutatur.match.dto;

import com.rutatur.common.enums.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record RouteMatchResponse(
        UUID id,
        UUID sharedRouteAId,
        UUID sharedRouteBId,
        String requesterUserId,
        String recipientUserId,
        MatchStatus status,
        double similarityScore,
        boolean consentA,
        boolean consentB,
        Instant createdAt,
        Instant updatedAt
) {
}
