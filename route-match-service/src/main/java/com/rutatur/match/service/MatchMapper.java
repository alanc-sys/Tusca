package com.rutatur.match.service;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.match.persistence.RouteMatchEntity;
import org.springframework.stereotype.Component;

/** Single mapping point from the lifecycle entity to the public contract. */
@Component
public class MatchMapper {

    public MatchDto toDto(RouteMatchEntity entity) {
        return new MatchDto(
                entity.getId(),
                entity.getRequesterUserId(),
                entity.getResponderUserId(),
                entity.getSharedRouteAId(),
                entity.getSharedRouteBId(),
                entity.getMatchScore().doubleValue(),
                entity.getMatchedPoiCount(),
                entity.getStatus(),
                entity.getRequesterAcceptedAt(),
                entity.getResponderAcceptedAt(),
                entity.getRequestedAt(),
                entity.getRespondedAt(),
                entity.getAcceptedAt());
    }
}
