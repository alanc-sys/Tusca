package com.rutatur.match.service;

import com.rutatur.common.dto.InternalItineraryDto;
import com.rutatur.common.dto.SharedRouteDto;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.match.client.ItineraryInternalClient;
import com.rutatur.match.config.MatchProperties;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in publication. The itinerary snapshot is read first, ownership is checked against the JWT
 * subject, and the share row is written last so an upstream failure leaves nothing behind.
 */
@Service
public class ShareService {

    private final ItineraryInternalClient itineraryClient;
    private final SharedRouteRepository sharedRouteRepository;
    private final MatchProperties properties;
    private final Clock clock;

    public ShareService(
            ItineraryInternalClient itineraryClient,
            SharedRouteRepository sharedRouteRepository,
            MatchProperties properties,
            Clock clock) {
        this.itineraryClient = itineraryClient;
        this.sharedRouteRepository = sharedRouteRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ShareResult share(UUID itineraryId, String userId) {
        InternalItineraryDto detail = itineraryClient.detail(itineraryId);
        if (!detail.ownerUserId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.shareTtl());
        Optional<SharedRouteEntity> existing = sharedRouteRepository.findByItineraryId(itineraryId);
        if (existing.isPresent()) {
            SharedRouteEntity share = existing.get();
            if (!share.getOwnerUserId().equals(userId)) {
                throw new ResourceNotFoundException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
            }
            if (share.isUsable(now)) {
                return new ShareResult(toDto(share), false);
            }
            share.reactivate(expiresAt);
            return new ShareResult(toDto(sharedRouteRepository.save(share)), false);
        }

        SharedRouteEntity entity = new SharedRouteEntity(
                UUID.randomUUID(),
                itineraryId,
                userId,
                BigDecimal.valueOf(detail.startPoint().latitude()),
                BigDecimal.valueOf(detail.startPoint().longitude()),
                detail.availableTime(),
                detail.transportMode().name(),
                detail.totalTime(),
                detail.poiIds(),
                detail.preferences().stream().map(Category::name).toList(),
                true,
                now,
                expiresAt);
        return new ShareResult(toDto(sharedRouteRepository.save(entity)), true);
    }

    private SharedRouteDto toDto(SharedRouteEntity entity) {
        return new SharedRouteDto(
                entity.getId(),
                entity.getItineraryId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getExpiresAt());
    }

    /** The DTO plus whether this call created the share (201) or returned an existing one (200). */
    public record ShareResult(SharedRouteDto dto, boolean created) {
    }
}
