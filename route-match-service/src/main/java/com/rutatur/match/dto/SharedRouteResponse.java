package com.rutatur.match.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SharedRouteResponse(
        UUID id,
        UUID itineraryId,
        String userId,
        String itineraryName,
        GeoPointDto startPoint,
        TransportMode transportMode,
        Set<String> poiIds,
        boolean active,
        Instant createdAt,
        Instant endedAt
) {
}
