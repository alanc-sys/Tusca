package com.rutatur.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.common.enums.Category;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MatchRequestTest {

    private static final String CALLER = "caller-sub";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SharedRouteRepository sharedRouteRepository = mock(SharedRouteRepository.class);
    private final RouteMatchRepository routeMatchRepository = mock(RouteMatchRepository.class);
    private final MatchRequestWriter writer = mock(MatchRequestWriter.class);
    private final SuggestionService suggestionService = mock(SuggestionService.class);
    private final MatchRequestService service = new MatchRequestService(
            sharedRouteRepository,
            routeMatchRepository,
            writer,
            suggestionService,
            new MatchMapper(),
            CLOCK);

    private SharedRouteEntity target;
    private RouteProfile callerProfile;

    @BeforeEach
    void setUp() {
        target = share("responder-sub", true, NOW.plusSeconds(3600));
        callerProfile = RouteProfile.from(share(CALLER, true, NOW.plusSeconds(3600)));

        when(sharedRouteRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(routeMatchRepository.findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
                        CALLER, target.getId(), MatchStatus.PENDING))
                .thenReturn(Optional.empty());
        when(suggestionService.bestPair(eq(CALLER), any(), any()))
                .thenReturn(new SuggestionService.ScoredPair(
                        callerProfile, new BigDecimal("0.5000"), List.of("p1")));
        when(writer.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPendingMatchWithResponderDerivedFromTarget() {
        MatchRequestService.RequestResult result = service.request(CALLER, target.getId());

        assertThat(result.created()).isTrue();
        MatchDto dto = result.dto();
        assertThat(dto.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(dto.requesterUserId()).isEqualTo(CALLER);
        assertThat(dto.responderUserId()).isEqualTo("responder-sub");
        assertThat(dto.requesterRouteId()).isEqualTo(callerProfile.id());
        assertThat(dto.responderRouteId()).isEqualTo(target.getId());
        assertThat(dto.matchScore()).isEqualTo(0.5);
        assertThat(dto.matchedPoiCount()).isEqualTo(1);
    }

    @Test
    void repeatedRequestReturnsExistingOpenMatch() {
        RouteMatchEntity existing = new RouteMatchEntity(
                UUID.randomUUID(),
                CALLER,
                "responder-sub",
                callerProfile.id(),
                target.getId(),
                new BigDecimal("0.5000"),
                1,
                List.of("p1"),
                MatchStatus.PENDING,
                NOW.minusSeconds(30));
        when(routeMatchRepository.findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
                        CALLER, target.getId(), MatchStatus.PENDING))
                .thenReturn(Optional.of(existing));

        MatchRequestService.RequestResult result = service.request(CALLER, target.getId());

        assertThat(result.created()).isFalse();
        assertThat(result.dto().id()).isEqualTo(existing.getId());
        verify(writer, never()).insert(any());
    }

    @Test
    void duplicateInsertRaceReturnsThePendingMatchCreatedByTheWinner() {
        RouteMatchEntity winner = new RouteMatchEntity(
                UUID.randomUUID(),
                CALLER,
                "responder-sub",
                callerProfile.id(),
                target.getId(),
                new BigDecimal("0.5000"),
                1,
                List.of("p1"),
                MatchStatus.PENDING,
                NOW);
        when(routeMatchRepository.findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
                        CALLER, target.getId(), MatchStatus.PENDING))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(writer.insert(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate request"));

        MatchRequestService.RequestResult result = service.request(CALLER, target.getId());

        assertThat(result.created()).isFalse();
        assertThat(result.dto().id()).isEqualTo(winner.getId());
        assertThat(result.dto().status()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    void duplicateInsertWithoutAPendingWinnerPropagatesTheConflict() {
        when(writer.insert(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate request"));

        assertThatThrownBy(() -> service.request(CALLER, target.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void targetThatIsNotActiveIsNotFound() {
        SharedRouteEntity inactive = share("responder-sub", false, NOW.plusSeconds(3600));
        when(sharedRouteRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.request(CALLER, inactive.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
    }

    @Test
    void ownTargetIsNotFound() {
        SharedRouteEntity own = share(CALLER, true, NOW.plusSeconds(3600));
        when(sharedRouteRepository.findById(own.getId())).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> service.request(CALLER, own.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
    }

    @Test
    void callerWithoutActiveShareGetsUnprocessable() {
        when(suggestionService.bestPair(eq(CALLER), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.request(CALLER, target.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NO_ACTIVE_SHARE);
        verify(writer, never()).insert(any());
    }

    private static SharedRouteEntity share(String owner, boolean active, Instant expiresAt) {
        return new SharedRouteEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                owner,
                BigDecimal.valueOf(-34.6037),
                BigDecimal.valueOf(-58.3816),
                120,
                "WALKING",
                90,
                List.of("p1", "p2"),
                List.of(Category.PARK.name()),
                active,
                NOW.minusSeconds(60),
                expiresAt);
    }
}
