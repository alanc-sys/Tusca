package com.rutatur.match.service;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.BusinessException;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import com.rutatur.match.similarity.RouteProfile;
import com.rutatur.match.similarity.SuggestionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code PENDING} request against another user's active share. The score is recomputed
 * from the caller's best active share so the stored value is reproducible, and an existing open
 * request for the same target is returned instead of duplicated.
 */
@Service
public class MatchRequestService {

    private final SharedRouteRepository sharedRouteRepository;
    private final RouteMatchRepository routeMatchRepository;
    private final SuggestionService suggestionService;
    private final MatchMapper mapper;
    private final Clock clock;

    public MatchRequestService(
            SharedRouteRepository sharedRouteRepository,
            RouteMatchRepository routeMatchRepository,
            SuggestionService suggestionService,
            MatchMapper mapper,
            Clock clock) {
        this.sharedRouteRepository = sharedRouteRepository;
        this.routeMatchRepository = routeMatchRepository;
        this.suggestionService = suggestionService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public RequestResult request(String requesterUserId, UUID targetRouteId) {
        Instant now = clock.instant();
        SharedRouteEntity target = sharedRouteRepository.findById(targetRouteId)
                .filter(share -> share.isUsable(now))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SHARE_NOT_FOUND, "Share not found"));
        if (target.getOwnerUserId().equals(requesterUserId)) {
            throw new ResourceNotFoundException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
        }

        Optional<RouteMatchEntity> open = routeMatchRepository
                .findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
                        requesterUserId, targetRouteId, MatchStatus.PENDING);
        if (open.isPresent()) {
            return new RequestResult(mapper.toDto(open.get()), false);
        }

        RouteProfile targetProfile = RouteProfile.from(target);
        SuggestionService.ScoredPair best = suggestionService.bestPair(requesterUserId, targetProfile, now);
        if (best == null) {
            throw new BusinessException(
                    ErrorCode.NO_ACTIVE_SHARE, "Caller has no active shared route");
        }

        RouteMatchEntity entity = new RouteMatchEntity(
                UUID.randomUUID(),
                requesterUserId,
                target.getOwnerUserId(),
                best.source().id(),
                target.getId(),
                best.score(),
                best.matchedPoiIds().size(),
                best.matchedPoiIds(),
                MatchStatus.PENDING,
                now);
        try {
            return new RequestResult(mapper.toDto(routeMatchRepository.saveAndFlush(entity)), true);
        } catch (DataIntegrityViolationException ex) {
            RouteMatchEntity existing = routeMatchRepository
                    .findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
                            requesterUserId, targetRouteId, MatchStatus.PENDING)
                    .orElseThrow(() -> ex);
            return new RequestResult(mapper.toDto(existing), false);
        }
    }

    /** The DTO plus whether this call created the request (201) or returned the open one (200). */
    public record RequestResult(MatchDto dto, boolean created) {
    }
}
