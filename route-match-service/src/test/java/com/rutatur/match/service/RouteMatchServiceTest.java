package com.rutatur.match.service;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.client.ItineraryInternalClient;
import com.rutatur.match.dto.ItinerarySnapshotDto;
import com.rutatur.match.dto.MatchContactResponse;
import com.rutatur.match.dto.MatchRequestDto;
import com.rutatur.match.dto.MatchSuggestionResponse;
import com.rutatur.match.dto.RouteMatchResponse;
import com.rutatur.match.dto.ShareRouteRequest;
import com.rutatur.match.dto.SharedRouteResponse;
import com.rutatur.match.entity.RouteMatchEntity;
import com.rutatur.match.entity.SharedRouteEntity;
import com.rutatur.match.repository.RouteMatchRepository;
import com.rutatur.match.repository.SharedRouteRepository;
import com.rutatur.match.state.MatchStateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteMatchServiceTest {

    @Mock
    private SharedRouteRepository sharedRouteRepository;

    @Mock
    private RouteMatchRepository routeMatchRepository;

    @Mock
    private ItineraryInternalClient itineraryClient;

    private RouteMatchService service;

    @BeforeEach
    void setUp() {
        service = new RouteMatchService(
                sharedRouteRepository,
                routeMatchRepository,
                itineraryClient,
                new SimilarityCalculator(),
                new MatchStateFactory());
    }

    @Test
    void shareRoute_validatesOwnershipAndStoresSnapshot() {
        UUID itineraryId = UUID.randomUUID();
        ItinerarySnapshotDto snapshot = new ItinerarySnapshotDto(
                itineraryId,
                "user-1",
                "Centro Histórico",
                new GeoPointDto(-31.4167, -64.1833),
                TransportMode.WALKING,
                List.of(new ItinerarySnapshotDto.StopSnapshot("poi-1"), new ItinerarySnapshotDto.StopSnapshot("poi-2")));

        when(itineraryClient.getItinerary(itineraryId)).thenReturn(snapshot);
        when(sharedRouteRepository.findByItineraryIdAndUserIdAndActiveTrue(itineraryId, "user-1"))
                .thenReturn(Optional.empty());
        when(sharedRouteRepository.save(any(SharedRouteEntity.class))).thenAnswer(inv -> {
            SharedRouteEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SharedRouteResponse response = service.shareRoute(
                new ShareRouteRequest(itineraryId), "user-1", "turista1", "turista1@rutatur.local");

        assertNotNull(response.id());
        assertEquals(itineraryId, response.itineraryId());
        assertEquals(Set.of("poi-1", "poi-2"), response.poiIds());
        assertTrue(response.active());
    }

    @Test
    void shareRoute_otherUsersItinerary_throwsForbidden() {
        UUID itineraryId = UUID.randomUUID();
        when(itineraryClient.getItinerary(itineraryId)).thenReturn(new ItinerarySnapshotDto(
                itineraryId, "user-2", "Ajeno", new GeoPointDto(-31.4, -64.1), TransportMode.WALKING, List.of()));

        RutaturException ex = assertThrows(RutaturException.class,
                () -> service.shareRoute(new ShareRouteRequest(itineraryId), "user-1", "t1", null));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void getSuggestions_filtersByThresholdAndReturnsEmptyListWhenNoneMatch() {
        SharedRouteEntity myRoute = buildSharedRoute(UUID.randomUUID(), "user-1", -31.4167, -64.1833,
                TransportMode.WALKING, Set.of("poi-1", "poi-2"));
        SharedRouteEntity closeMatch = buildSharedRoute(UUID.randomUUID(), "user-2", -31.4170, -64.1830,
                TransportMode.WALKING, Set.of("poi-1", "poi-2"));
        SharedRouteEntity farNoOverlap = buildSharedRoute(UUID.randomUUID(), "user-3", -32.0, -65.0,
                TransportMode.DRIVING, Set.of("poi-99"));

        when(sharedRouteRepository.findById(myRoute.getId())).thenReturn(Optional.of(myRoute));
        when(sharedRouteRepository.findByActiveTrueAndUserIdNot("user-1"))
                .thenReturn(List.of(closeMatch, farNoOverlap));

        List<MatchSuggestionResponse> suggestions = service.getSuggestions(myRoute.getId(), "user-1");

        assertEquals(1, suggestions.size());
        assertEquals(closeMatch.getId(), suggestions.get(0).targetRouteId());
        assertTrue(suggestions.get(0).similarityScore() >= SimilarityCalculator.MIN_SIMILARITY_THRESHOLD);

        when(sharedRouteRepository.findByActiveTrueAndUserIdNot("user-1")).thenReturn(List.of(farNoOverlap));
        assertTrue(service.getSuggestions(myRoute.getId(), "user-1").isEmpty());
    }

    @Test
    void matchLifecycle_pendingToAccepted_enforcesStatePatternAndMutualConsent() {
        SharedRouteEntity routeA = buildSharedRoute(UUID.randomUUID(), "user-1", -31.4167, -64.1833,
                TransportMode.WALKING, Set.of("poi-1"));
        routeA.setContactUsername("turista1");
        routeA.setContactEmail("turista1@rutatur.local");

        SharedRouteEntity routeB = buildSharedRoute(UUID.randomUUID(), "user-2", -31.4170, -64.1830,
                TransportMode.WALKING, Set.of("poi-1"));
        routeB.setContactUsername("turista2");
        routeB.setContactEmail("turista2@rutatur.local");

        when(sharedRouteRepository.findById(routeA.getId())).thenReturn(Optional.of(routeA));
        when(sharedRouteRepository.findById(routeB.getId())).thenReturn(Optional.of(routeB));
        when(routeMatchRepository.existsActiveMatchBetween(eq(routeA.getId()), eq(routeB.getId()), any()))
                .thenReturn(false);

        UUID matchId = UUID.randomUUID();
        when(routeMatchRepository.save(any(RouteMatchEntity.class))).thenAnswer(inv -> {
            RouteMatchEntity m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(matchId);
            }
            return m;
        });

        RouteMatchResponse created = service.requestMatch(new MatchRequestDto(routeA.getId(), routeB.getId()), "user-1");
        assertEquals(MatchStatus.PENDING, created.status());
        assertTrue(created.consentA());
        assertFalse(created.consentB());

        RouteMatchEntity persisted = new RouteMatchEntity();
        persisted.setId(matchId);
        persisted.setSharedRouteA(routeA);
        persisted.setSharedRouteB(routeB);
        persisted.setStatus(MatchStatus.PENDING);
        persisted.setConsentA(true);
        persisted.setConsentB(false);
        persisted.setSimilarityScore(created.similarityScore());
        when(routeMatchRepository.findById(matchId)).thenReturn(Optional.of(persisted));

        RutaturException contactPendingEx = assertThrows(RutaturException.class,
                () -> service.getMatchContact(matchId, "user-1"));
        assertEquals(ErrorCode.CONSENT_REQUIRED, contactPendingEx.getErrorCode());

        RutaturException selfAcceptEx = assertThrows(RutaturException.class,
                () -> service.acceptMatch(matchId, "user-1"));
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, selfAcceptEx.getErrorCode());

        RouteMatchResponse accepted = service.acceptMatch(matchId, "user-2");
        assertEquals(MatchStatus.ACCEPTED, accepted.status());
        assertTrue(accepted.consentA());
        assertTrue(accepted.consentB());

        MatchContactResponse contactForA = service.getMatchContact(matchId, "user-1");
        assertEquals("turista2", contactForA.counterpartyUsername());
        assertEquals("turista2@rutatur.local", contactForA.counterpartyEmail());

        RutaturException terminalEx = assertThrows(RutaturException.class,
                () -> service.rejectMatch(matchId, "user-2"));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, terminalEx.getErrorCode());
    }

    private SharedRouteEntity buildSharedRoute(
            UUID id,
            String userId,
            double lat,
            double lon,
            TransportMode mode,
            Set<String> poiIds) {
        SharedRouteEntity entity = new SharedRouteEntity();
        entity.setId(id);
        entity.setItineraryId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setItineraryName("Ruta " + userId);
        entity.setStartLatitude(lat);
        entity.setStartLongitude(lon);
        entity.setTransportMode(mode);
        entity.setPoiIds(poiIds);
        entity.setActive(true);
        return entity;
    }
}
