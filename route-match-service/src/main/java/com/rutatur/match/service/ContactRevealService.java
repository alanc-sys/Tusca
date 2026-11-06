package com.rutatur.match.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.ContactDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ForbiddenActionException;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.match.client.KeycloakAdminClient;
import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import com.rutatur.match.persistence.UserContactViewEntity;
import com.rutatur.match.persistence.UserContactViewRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Contact reveal for accepted participants only. Keycloak is read before the audit row is written,
 * so a provider failure leaves no trace and no contact data is ever stored on the share.
 */
@Service
public class ContactRevealService {

    private final RouteMatchRepository routeMatchRepository;
    private final UserContactViewRepository contactViewRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ContactRevealService(
            RouteMatchRepository routeMatchRepository,
            UserContactViewRepository contactViewRepository,
            KeycloakAdminClient keycloakAdminClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.routeMatchRepository = routeMatchRepository;
        this.contactViewRepository = contactViewRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ContactDto reveal(UUID matchId, String viewerUserId) {
        RouteMatchEntity match = routeMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MATCH_NOT_FOUND, "Match not found"));
        if (!match.isParticipant(viewerUserId) || match.getStatus() != MatchStatus.ACCEPTED) {
            throw new ForbiddenActionException(
                    ErrorCode.CONTACT_NOT_REVEALED, "Contact not revealed");
        }

        String viewedUserId = match.counterpartOf(viewerUserId);
        ContactDto contact = keycloakAdminClient.lookupUser(viewedUserId);
        contactViewRepository.save(new UserContactViewEntity(
                UUID.randomUUID(),
                matchId,
                viewerUserId,
                viewedUserId,
                clock.instant(),
                objectMapper.valueToTree(contact)));
        return contact;
    }
}
