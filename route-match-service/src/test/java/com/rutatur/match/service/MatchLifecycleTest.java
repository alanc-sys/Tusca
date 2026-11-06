package com.rutatur.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ConflictException;
import com.rutatur.common.exception.ForbiddenActionException;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchLifecycleTest {

    private static final String REQUESTER = "requester-sub";
    private static final String RESPONDER = "responder-sub";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RouteMatchRepository repository = mock(RouteMatchRepository.class);
    private final MatchLifecycleService service =
            new MatchLifecycleService(repository, new MatchMapper(), CLOCK);

    private RouteMatchEntity match;

    @BeforeEach
    void setUp() {
        match = new RouteMatchEntity(
                UUID.randomUUID(),
                REQUESTER,
                RESPONDER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("0.7500"),
                2,
                List.of("p1", "p2"),
                MatchStatus.PENDING,
                NOW.minusSeconds(60));
        when(repository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));
    }

    @Test
    void firstAcceptanceKeepsMatchPending() {
        MatchDto dto = service.accept(match.getId(), RESPONDER);

        assertThat(dto.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(dto.responderAcceptedAt()).isEqualTo(NOW);
        assertThat(dto.requesterAcceptedAt()).isNull();
        assertThat(dto.acceptedAt()).isNull();
    }

    @Test
    void secondAcceptanceCompletesTheMatch() {
        service.accept(match.getId(), RESPONDER);
        MatchDto dto = service.accept(match.getId(), REQUESTER);

        assertThat(dto.status()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(dto.acceptedAt()).isEqualTo(NOW);
        assertThat(dto.respondedAt()).isEqualTo(NOW);
        assertThat(dto.requesterAcceptedAt()).isNotNull();
        assertThat(dto.responderAcceptedAt()).isNotNull();
    }

    @Test
    void repeatedAcceptanceFromSameSideRecordsConsentOnce() {
        MatchDto first = service.accept(match.getId(), RESPONDER);
        MatchDto second = service.accept(match.getId(), RESPONDER);

        assertThat(second.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(second.responderAcceptedAt()).isEqualTo(first.responderAcceptedAt());
    }

    @Test
    void acceptOnAcceptedMatchIsConflict() {
        service.accept(match.getId(), RESPONDER);
        service.accept(match.getId(), REQUESTER);

        assertThatThrownBy(() -> service.accept(match.getId(), REQUESTER))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo(ErrorCode.MATCH_TRANSITION_INVALID);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
    }

    @Test
    void rejectOnRejectedMatchIsConflict() {
        service.reject(match.getId(), REQUESTER);

        assertThatThrownBy(() -> service.reject(match.getId(), RESPONDER))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo(ErrorCode.MATCH_TRANSITION_INVALID);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void rejectIsTerminalAndAcceptAfterRejectIsConflict() {
        MatchDto rejected = service.reject(match.getId(), RESPONDER);

        assertThat(rejected.status()).isEqualTo(MatchStatus.REJECTED);
        assertThat(rejected.respondedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.accept(match.getId(), REQUESTER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownMatchIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.accept(unknown, REQUESTER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.MATCH_NOT_FOUND);
    }

    @Test
    void nonParticipantIsForbidden() {
        assertThatThrownBy(() -> service.accept(match.getId(), "stranger"))
                .isInstanceOf(ForbiddenActionException.class)
                .extracting(ex -> ((ForbiddenActionException) ex).getCode())
                .isEqualTo(ErrorCode.MATCH_ACCESS_DENIED);
    }
}
