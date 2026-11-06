package com.rutatur.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.ContactDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ForbiddenActionException;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.client.KeycloakAdminClient;
import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import com.rutatur.match.persistence.UserContactViewEntity;
import com.rutatur.match.persistence.UserContactViewRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContactRevealTest {

    private static final String REQUESTER = "requester-sub";
    private static final String RESPONDER = "responder-sub";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RouteMatchRepository matchRepository = mock(RouteMatchRepository.class);
    private final UserContactViewRepository contactViewRepository = mock(UserContactViewRepository.class);
    private final KeycloakAdminClient keycloakAdminClient = mock(KeycloakAdminClient.class);
    private final ContactRevealService service = new ContactRevealService(
            matchRepository,
            contactViewRepository,
            keycloakAdminClient,
            new ObjectMapper(),
            CLOCK);

    private RouteMatchEntity match;

    @BeforeEach
    void setUp() {
        match = new RouteMatchEntity(
                UUID.randomUUID(),
                REQUESTER,
                RESPONDER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("0.8000"),
                1,
                List.of("p1"),
                MatchStatus.PENDING,
                NOW.minusSeconds(60));
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(keycloakAdminClient.lookupUser(RESPONDER))
                .thenReturn(new ContactDto(RESPONDER, "responder", "responder@example.com"));
        when(keycloakAdminClient.lookupUser(REQUESTER))
                .thenReturn(new ContactDto(REQUESTER, "requester", "requester@example.com"));
    }

    @Test
    void acceptedParticipantReceivesContactAndAuditRow() {
        acceptBothSides();

        ContactDto contact = service.reveal(match.getId(), REQUESTER);

        assertThat(contact.userId()).isEqualTo(RESPONDER);
        assertThat(contact.username()).isEqualTo("responder");
        assertThat(contact.email()).isEqualTo("responder@example.com");

        ArgumentCaptor<UserContactViewEntity> captor =
                ArgumentCaptor.forClass(UserContactViewEntity.class);
        verify(contactViewRepository).save(captor.capture());
        UserContactViewEntity audit = captor.getValue();
        assertThat(audit.getRouteMatchId()).isEqualTo(match.getId());
        assertThat(audit.getViewerUserId()).isEqualTo(REQUESTER);
        assertThat(audit.getViewedUserId()).isEqualTo(RESPONDER);
        assertThat(audit.getRevealedAt()).isEqualTo(NOW);
        assertThat(audit.getContactInfo().get("email").asText())
                .isEqualTo("responder@example.com");
    }

    @Test
    void pendingMatchIsBlockedWithoutAudit() {
        assertThatThrownBy(() -> service.reveal(match.getId(), REQUESTER))
                .isInstanceOf(ForbiddenActionException.class)
                .extracting(ex -> ((ForbiddenActionException) ex).getCode())
                .isEqualTo(ErrorCode.CONTACT_NOT_REVEALED);
        verify(contactViewRepository, never()).save(any());
    }

    @Test
    void rejectedMatchIsBlockedWithoutAudit() {
        match.reject(NOW);

        assertThatThrownBy(() -> service.reveal(match.getId(), REQUESTER))
                .isInstanceOf(ForbiddenActionException.class);
        verify(contactViewRepository, never()).save(any());
    }

    @Test
    void nonParticipantIsBlockedWithoutAudit() {
        acceptBothSides();

        assertThatThrownBy(() -> service.reveal(match.getId(), "stranger"))
                .isInstanceOf(ForbiddenActionException.class);
        verify(contactViewRepository, never()).save(any());
    }

    @Test
    void unknownMatchIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(matchRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reveal(unknown, REQUESTER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.MATCH_NOT_FOUND);
    }

    @Test
    void providerFailureLeavesNoAuditRow() {
        acceptBothSides();
        when(keycloakAdminClient.lookupUser(eq(RESPONDER))).thenThrow(
                new UpstreamServiceException(ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.reveal(match.getId(), REQUESTER))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.CONTACT_PROVIDER_UNAVAILABLE);
        verify(contactViewRepository, never()).save(any());
    }

    private void acceptBothSides() {
        match.recordConsent(true, NOW);
        match.recordConsent(false, NOW);
        match.complete(NOW);
    }
}
