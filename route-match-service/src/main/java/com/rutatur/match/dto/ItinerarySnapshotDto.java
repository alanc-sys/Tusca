package com.rutatur.match.dto;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;

import java.util.List;
import java.util.UUID;

public record ItinerarySnapshotDto(
        UUID id,
        String userId,
        String name,
        GeoPointDto startPoint,
        TransportMode transportMode,
        List<StopSnapshot> stops
) {
    public record StopSnapshot(String poiId) {
    }
}
