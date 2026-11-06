package com.rutatur.match.similarity;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.Category;
import com.rutatur.match.persistence.SharedRouteEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable view of a share used for scoring; deliberately contact-free. */
public record RouteProfile(
        UUID id,
        String ownerUserId,
        GeoPointDto startPoint,
        List<String> poiIds,
        Set<Category> categories,
        int availableTime,
        java.time.Instant createdAt) {

    public static RouteProfile from(SharedRouteEntity entity) {
        return new RouteProfile(
                entity.getId(),
                entity.getOwnerUserId(),
                new GeoPointDto(
                        entity.getStartLat().doubleValue(), entity.getStartLng().doubleValue()),
                List.copyOf(entity.getPoiIds()),
                entity.getCategories().stream()
                        .map(Category::valueOf)
                        .collect(Collectors.toUnmodifiableSet()),
                entity.getAvailableTime(),
                entity.getCreatedAt());
    }
}
