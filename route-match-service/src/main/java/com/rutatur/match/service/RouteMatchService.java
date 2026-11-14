package com.rutatur.match.service;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.MatchStatus;
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
import com.rutatur.match.state.MatchState;
import com.rutatur.match.state.MatchStateFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RouteMatchService {

    private final SharedRouteRepository sharedRouteRepository;
    private final RouteMatchRepository routeMatchRepository;
    private final ItineraryInternalClient itineraryClient;
    private final SimilarityCalculator similarityCalculator;
    private final MatchStateFactory stateFactory;

    public RouteMatchService(
            SharedRouteRepository sharedRouteRepository,
            RouteMatchRepository routeMatchRepository,
            ItineraryInternalClient itineraryClient,
            SimilarityCalculator similarityCalculator,
            MatchStateFactory stateFactory) {
        this.sharedRouteRepository = sharedRouteRepository;
        this.routeMatchRepository = routeMatchRepository;
        this.itineraryClient = itineraryClient;
        this.similarityCalculator = similarityCalculator;
        this.stateFactory = stateFactory;
    }

    @Transactional
    public SharedRouteResponse shareRoute(
            ShareRouteRequest request,
            String userId,
            String username,
            String email) {
        ItinerarySnapshotDto itinerary = itineraryClient.getItinerary(request.itineraryId());

        if (!userId.equals(itinerary.userId())) {
            throw new RutaturException(ErrorCode.FORBIDDEN, "No podés compartir un itinerario que no te pertenece");
        }

        sharedRouteRepository.findByItineraryIdAndUserIdAndActiveTrue(request.itineraryId(), userId)
                .ifPresent(existing -> {
                    throw new RutaturException(
                            ErrorCode.DUPLICATE_RESOURCE,
                            "El itinerario ya se encuentra compartido activamente");
                });

        SharedRouteEntity entity = new SharedRouteEntity();
        entity.setItineraryId(itinerary.id());
        entity.setUserId(userId);
        entity.setContactUsername(username != null ? username : userId);
        entity.setContactEmail(email);
        entity.setItineraryName(itinerary.name());
        entity.setStartLatitude(itinerary.startPoint().latitude());
        entity.setStartLongitude(itinerary.startPoint().longitude());
        entity.setTransportMode(itinerary.transportMode());

        Set<String> poiIds = itinerary.stops() == null ? Set.of() : itinerary.stops().stream()
                .map(ItinerarySnapshotDto.StopSnapshot::poiId)
                .collect(Collectors.toSet());
        entity.setPoiIds(poiIds);
        entity.setActive(true);

        return toSharedRouteResponse(sharedRouteRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SharedRouteResponse> getShareableRoutes(String userId) {
        return sharedRouteRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSharedRouteResponse)
                .toList();
    }

    @Transactional
    public void unshareRoute(UUID routeId, String userId) {
        SharedRouteEntity route = findSharedRouteOrThrow(routeId);
        if (!route.getUserId().equals(userId)) {
            throw new RutaturException(ErrorCode.FORBIDDEN, "No tenés permiso para dejar de compartir esta ruta");
        }
        route.deactivate();
        sharedRouteRepository.save(route);
    }

    @Transactional(readOnly = true)
    public List<MatchSuggestionResponse> getSuggestions(UUID routeId, String userId) {
        SharedRouteEntity myRoute = resolveSourceRoute(routeId, userId);
        if (myRoute == null) {
            return List.of();
        }

        return sharedRouteRepository.findByActiveTrueAndUserIdNot(userId).stream()
                .map(candidate -> new ScoredCandidate(
                        candidate,
                        similarityCalculator.calculate(myRoute, candidate),
                        similarityCalculator.intersectPois(myRoute, candidate)))
                .filter(sc -> sc.score() >= SimilarityCalculator.MIN_SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .map(sc -> new MatchSuggestionResponse(
                        myRoute.getId(),
                        sc.candidate().getId(),
                        sc.candidate().getItineraryId(),
                        sc.candidate().getItineraryName(),
                        sc.candidate().getTransportMode(),
                        sc.sharedPoiIds(),
                        sc.score()))
                .toList();
    }

    @Transactional
    public RouteMatchResponse requestMatch(MatchRequestDto request, String userId) {
        SharedRouteEntity myRoute = findSharedRouteOrThrow(request.myRouteId());
        SharedRouteEntity targetRoute = findSharedRouteOrThrow(request.targetRouteId());

        if (!myRoute.getUserId().equals(userId)) {
            throw new RutaturException(ErrorCode.FORBIDDEN, "La ruta origen no te pertenece");
        }
        if (!myRoute.isActive() || !targetRoute.isActive()) {
            throw new RutaturException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ambas rutas deben estar compartidas activamente para solicitar un match");
        }
        if (targetRoute.getUserId().equals(userId)) {
            throw new RutaturException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "No podés solicitar un match con tu propia ruta");
        }

        boolean alreadyExists = routeMatchRepository.existsActiveMatchBetween(
                myRoute.getId(),
                targetRoute.getId(),
                List.of(MatchStatus.PENDING, MatchStatus.ACCEPTED));
        if (alreadyExists) {
            throw new RutaturException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Ya existe un match pendiente o aceptado entre estas rutas");
        }

        double score = similarityCalculator.calculate(myRoute, targetRoute);

        RouteMatchEntity match = new RouteMatchEntity();
        match.setSharedRouteA(myRoute);
        match.setSharedRouteB(targetRoute);
        match.setStatus(MatchStatus.PENDING);
        match.setSimilarityScore(score);
        match.setConsentA(true);
        match.setConsentB(false);

        return toMatchResponse(routeMatchRepository.save(match));
    }

    @Transactional(readOnly = true)
    public List<RouteMatchResponse> listMatches(String userId) {
        return routeMatchRepository.findAllByParticipant(userId).stream()
                .map(this::toMatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteMatchResponse getMatch(UUID matchId, String userId) {
        RouteMatchEntity match = findMatchOrThrow(matchId);
        verifyParticipant(match, userId);
        return toMatchResponse(match);
    }

    @Transactional
    public RouteMatchResponse acceptMatch(UUID matchId, String userId) {
        RouteMatchEntity match = findMatchOrThrow(matchId);
        MatchState currentState = stateFactory.of(match.getStatus());
        currentState.accept(match, userId);
        return toMatchResponse(routeMatchRepository.save(match));
    }

    @Transactional
    public RouteMatchResponse rejectMatch(UUID matchId, String userId) {
        RouteMatchEntity match = findMatchOrThrow(matchId);
        MatchState currentState = stateFactory.of(match.getStatus());
        currentState.reject(match, userId);
        return toMatchResponse(routeMatchRepository.save(match));
    }

    @Transactional(readOnly = true)
    public MatchContactResponse getMatchContact(UUID matchId, String userId) {
        RouteMatchEntity match = findMatchOrThrow(matchId);
        verifyParticipant(match, userId);

        MatchState currentState = stateFactory.of(match.getStatus());
        if (!currentState.canRevealContact()) {
            throw new RutaturException(
                    ErrorCode.CONSENT_REQUIRED,
                    "El contacto solo se revela cuando ambos usuarios aceptaron el match");
        }

        SharedRouteEntity counterparty = match.getCounterpartyRoute(userId);
        return new MatchContactResponse(
                match.getId(),
                counterparty.getUserId(),
                counterparty.getContactUsername(),
                counterparty.getContactEmail(),
                counterparty.getItineraryName());
    }

    private SharedRouteEntity resolveSourceRoute(UUID routeId, String userId) {
        if (routeId != null) {
            SharedRouteEntity route = findSharedRouteOrThrow(routeId);
            if (!route.getUserId().equals(userId)) {
                throw new RutaturException(ErrorCode.FORBIDDEN, "No tenés permiso para consultar sugerencias de esta ruta");
            }
            if (!route.isActive()) {
                throw new RutaturException(ErrorCode.BUSINESS_RULE_VIOLATION, "La ruta ya no está compartida activamente");
            }
            return route;
        }
        return sharedRouteRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private SharedRouteEntity findSharedRouteOrThrow(UUID routeId) {
        return sharedRouteRepository.findById(routeId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Ruta compartida no encontrada: " + routeId));
    }

    private RouteMatchEntity findMatchOrThrow(UUID matchId) {
        return routeMatchRepository.findById(matchId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Match no encontrado: " + matchId));
    }

    private void verifyParticipant(RouteMatchEntity match, String userId) {
        if (!match.involvesUser(userId)) {
            throw new RutaturException(ErrorCode.FORBIDDEN, "No tenés permiso para acceder a este match");
        }
    }

    private SharedRouteResponse toSharedRouteResponse(SharedRouteEntity entity) {
        return new SharedRouteResponse(
                entity.getId(),
                entity.getItineraryId(),
                entity.getUserId(),
                entity.getItineraryName(),
                new GeoPointDto(entity.getStartLatitude(), entity.getStartLongitude()),
                entity.getTransportMode(),
                entity.getPoiIds(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getEndedAt());
    }

    private RouteMatchResponse toMatchResponse(RouteMatchEntity match) {
        return new RouteMatchResponse(
                match.getId(),
                match.getSharedRouteA().getId(),
                match.getSharedRouteB().getId(),
                match.getSharedRouteA().getUserId(),
                match.getSharedRouteB().getUserId(),
                match.getStatus(),
                match.getSimilarityScore(),
                match.isConsentA(),
                match.isConsentB(),
                match.getCreatedAt(),
                match.getUpdatedAt());
    }

    private record ScoredCandidate(
            SharedRouteEntity candidate,
            double score,
            Set<String> sharedPoiIds) {
    }
}
