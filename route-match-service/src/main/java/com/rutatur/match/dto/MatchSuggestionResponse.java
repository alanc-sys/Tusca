package com.rutatur.match.dto;

import com.rutatur.common.enums.TransportMode;

import java.util.Set;
import java.util.UUID;

public record MatchSuggestionResponse(
        UUID myRouteId,
        UUID targetRouteId,
        UUID targetItineraryId,
        String targetItineraryName,
        TransportMode targetTransportMode,
        Set<String> sharedPoiIds,
        double similarityScore
) {
}
