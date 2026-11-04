package com.rutatur.match.service;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ConflictException;
import com.rutatur.common.exception.ForbiddenActionException;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutual-consent state machine. The row is read under a write lock, so a side can only consent
 * once and the match flips to {@code ACCEPTED} only when both sides are present; any accept or
 * reject on a terminal match is refused with {@code 409}.
 */
@Service
public class MatchLifecycleService {

    private final RouteMatchRepository routeMatchRepository;
    private final MatchMapper mapper;
    private final Clock clock;

    public MatchLifecycleService(
            RouteMatchRepository routeMatchRepository, MatchMapper mapper, Clock clock) {
        this.routeMatchRepository = routeMatchRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public MatchDto accept(UUID matchId, String userId) {
        RouteMatchEntity match = requireParticipantForUpdate(matchId, userId);
        if (match.getStatus() != MatchStatus.PENDING) {
            throw transitionInvalid();
        }

        Instant now = clock.instant();
        match.recordConsent(match.isRequester(userId), now);
        match.complete(now);
        return mapper.toDto(match);
    }

    @Transactional
    public MatchDto reject(UUID matchId, String userId) {
        RouteMatchEntity match = requireParticipantForUpdate(matchId, userId);
        if (match.getStatus() != MatchStatus.PENDING) {
            throw transitionInvalid();
        }

        match.reject(clock.instant());
        return mapper.toDto(match);
    }

    private RouteMatchEntity requireParticipantForUpdate(UUID matchId, String userId) {
        RouteMatchEntity match = routeMatchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MATCH_NOT_FOUND, "Match not found"));
        if (!match.isParticipant(userId)) {
            throw new ForbiddenActionException(
                    ErrorCode.MATCH_ACCESS_DENIED, "Match access denied");
        }
        return match;
    }

    private static ConflictException transitionInvalid() {
        return new ConflictException(
                ErrorCode.MATCH_TRANSITION_INVALID, "Match transition is not allowed");
    }
}
