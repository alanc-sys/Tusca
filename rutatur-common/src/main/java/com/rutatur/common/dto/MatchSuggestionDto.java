package com.rutatur.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Similarity suggestion. Exposes no contact data, user id or coordinates. */
public record MatchSuggestionDto(
        UUID targetRouteId,
        UUID sourceRouteId,
        double matchScore,
        int matchedPoiCount,
        List<String> matchedPoiIds,
        Instant sharedAt) {
}
