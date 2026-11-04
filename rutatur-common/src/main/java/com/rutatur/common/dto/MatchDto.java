package com.rutatur.common.dto;

import com.rutatur.common.enums.MatchStatus;
import java.time.Instant;
import java.util.UUID;

/** Match lifecycle projection. */
public record MatchDto(
        UUID id,
        String requesterUserId,
        String responderUserId,
        UUID requesterRouteId,
        UUID responderRouteId,
        double matchScore,
        int matchedPoiCount,
        MatchStatus status,
        Instant requesterAcceptedAt,
        Instant responderAcceptedAt,
        Instant requestedAt,
        Instant respondedAt,
        Instant acceptedAt) {
}
