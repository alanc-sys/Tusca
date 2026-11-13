package com.rutatur.itinerary.service;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.itinerary.client.OsrmClient;
import com.rutatur.itinerary.client.PoiInternalClient;
import com.rutatur.itinerary.dto.GenerateItineraryRequest;
import com.rutatur.itinerary.dto.GeneratedItineraryResponse;
import com.rutatur.itinerary.dto.ItineraryStopDto;
import com.rutatur.itinerary.dto.SaveItineraryRequest;
import com.rutatur.itinerary.dto.SavedItineraryResponse;
import com.rutatur.itinerary.entity.ItineraryPoiEntity;
import com.rutatur.itinerary.entity.SavedItineraryEntity;
import com.rutatur.itinerary.repository.SavedItineraryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orquestador de generación heurística de itinerarios y CRUD seguro por propietario.
 */
@Service
public class ItineraryService {

    private static final int MAX_CANDIDATES_FOR_MATRIX = 15;

    private final PoiInternalClient poiInternalClient;
    private final OsrmClient osrmClient;
    private final SavedItineraryRepository itineraryRepository;

    public ItineraryService(
            PoiInternalClient poiInternalClient,
            OsrmClient osrmClient,
            SavedItineraryRepository itineraryRepository
    ) {
        this.poiInternalClient = poiInternalClient;
        this.osrmClient = osrmClient;
        this.itineraryRepository = itineraryRepository;
    }

    public GeneratedItineraryResponse generateItinerary(GenerateItineraryRequest request) {
        int radius = request.resolvedRadiusMeters();
        if (radius < 100 || radius > 20_000) {
            throw new RutaturException(ErrorCode.VALIDATION_ERROR, "El radio debe estar entre 100 y 20000 metros");
        }

        TransportMode mode = request.resolvedTransportMode();
        List<PoiDto> rawCandidates = poiInternalClient.searchCandidatePois(
                request.startPoint().latitude(),
                request.startPoint().longitude(),
                radius,
                request.categories()
        );

        if (rawCandidates.isEmpty()) {
            throw new RutaturException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "No se encontraron POIs disponibles en la zona solicitada"
            );
        }

        // Deduplica por ID (RN-02) y limita candidatos para matriz OSRM
        List<PoiDto> candidates = deduplicateAndLimit(rawCandidates);

        List<GeoPointDto> points = new ArrayList<>();
        points.add(request.startPoint());
        candidates.forEach(p -> points.add(p.location()));

        OsrmClient.TravelMatrix matrix = osrmClient.fetchMatrix(points, mode);

        return buildGreedyRoute(request, mode, candidates, matrix);
    }

    @Transactional
    public SavedItineraryResponse saveItinerary(String userId, SaveItineraryRequest request) {
        SavedItineraryEntity entity = new SavedItineraryEntity(
                userId,
                request.name(),
                request.totalDurationMinutes(),
                request.startPoint().latitude(),
                request.startPoint().longitude(),
                request.transportMode(),
                Instant.now()
        );

        request.stops().forEach(stop -> entity.addStop(new ItineraryPoiEntity(
                stop.poiId(),
                stop.visitOrder(),
                stop.stayDurationMinutes(),
                stop.travelTimeToNextMinutes(),
                stop.distanceToNextMeters()
        )));

        return toSavedResponse(itineraryRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SavedItineraryResponse> listUserItineraries(String userId) {
        return itineraryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSavedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedItineraryResponse getItinerary(UUID id, String userId) {
        SavedItineraryEntity entity = findAndVerifyOwner(id, userId);
        return toSavedResponse(entity);
    }

    @Transactional
    public void deleteItinerary(UUID id, String userId) {
        SavedItineraryEntity entity = findAndVerifyOwner(id, userId);
        itineraryRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public SavedItineraryResponse getItineraryInternal(UUID id) {
        SavedItineraryEntity entity = itineraryRepository.findById(id)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Itinerario no encontrado: " + id
                ));
        return toSavedResponse(entity);
    }

    private SavedItineraryEntity findAndVerifyOwner(UUID id, String userId) {
        SavedItineraryEntity entity = itineraryRepository.findById(id)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Itinerario no encontrado: " + id
                ));

        if (!entity.getUserId().equals(userId)) {
            throw new RutaturException(
                    ErrorCode.FORBIDDEN,
                    "No tenés permiso para acceder o modificar un itinerario de otro usuario"
            );
        }
        return entity;
    }

    private List<PoiDto> deduplicateAndLimit(List<PoiDto> rawCandidates) {
        Set<String> seenIds = new HashSet<>();
        List<PoiDto> unique = new ArrayList<>();
        for (PoiDto poi : rawCandidates) {
            if (poi.id() != null && seenIds.add(poi.id())) {
                unique.add(poi);
                if (unique.size() >= MAX_CANDIDATES_FOR_MATRIX) {
                    break;
                }
            }
        }
        return unique;
    }

    /**
     * Heurística greedy: desde la posición actual (0 = startPoint), selecciona el siguiente POI
     * no visitado cuyo (traslado + permanencia) quepa en el tiempo restante (RN-01, RN-02).
     */
    private GeneratedItineraryResponse buildGreedyRoute(
            GenerateItineraryRequest request,
            TransportMode mode,
            List<PoiDto> candidates,
            OsrmClient.TravelMatrix matrix
    ) {
        int remainingMinutes = request.availableTimeMinutes();
        int totalUsedMinutes = 0;
        int currentMatrixIndex = 0;
        boolean[] visited = new boolean[candidates.size()];

        List<Integer> selectedIndices = new ArrayList<>();
        List<Integer> legTravelMinutes = new ArrayList<>();
        List<Double> legDistances = new ArrayList<>();

        while (true) {
            int bestCandidate = -1;
            int bestCost = Integer.MAX_VALUE;

            for (int i = 0; i < candidates.size(); i++) {
                if (visited[i]) {
                    continue;
                }
                int matrixTargetIdx = i + 1;
                int travelMin = matrix.durationsMinutes()[currentMatrixIndex][matrixTargetIdx];
                int stayMin = candidates.get(i).estimatedStayMinutes();
                int stepCost = travelMin + stayMin;

                if (stepCost <= remainingMinutes && stepCost < bestCost) {
                    bestCost = stepCost;
                    bestCandidate = i;
                }
            }

            if (bestCandidate == -1) {
                break;
            }

            int matrixTargetIdx = bestCandidate + 1;
            int travelMin = matrix.durationsMinutes()[currentMatrixIndex][matrixTargetIdx];
            double distanceMeters = matrix.distancesMeters()[currentMatrixIndex][matrixTargetIdx];

            visited[bestCandidate] = true;
            remainingMinutes -= bestCost;
            totalUsedMinutes += bestCost;
            currentMatrixIndex = matrixTargetIdx;

            selectedIndices.add(bestCandidate);
            legTravelMinutes.add(travelMin);
            legDistances.add(distanceMeters);
        }

        if (selectedIndices.isEmpty()) {
            throw new RutaturException(
                    ErrorCode.INSUFFICIENT_TIME,
                    "El tiempo disponible (" + request.availableTimeMinutes()
                            + " min) es insuficiente para visitar al menos un POI"
            );
        }

        List<ItineraryStopDto> route = new ArrayList<>();
        for (int order = 0; order < selectedIndices.size(); order++) {
            PoiDto poi = candidates.get(selectedIndices.get(order));
            int travelToNext = (order + 1 < selectedIndices.size()) ? legTravelMinutes.get(order + 1) : 0;
            double distToNext = (order + 1 < selectedIndices.size()) ? legDistances.get(order + 1) : 0.0;

            route.add(new ItineraryStopDto(
                    order + 1,
                    poi,
                    poi.estimatedStayMinutes(),
                    travelToNext,
                    distToNext
            ));
        }

        return new GeneratedItineraryResponse(
                request.startPoint(),
                mode,
                totalUsedMinutes,
                request.availableTimeMinutes(),
                matrix.osrmUsed(),
                route
        );
    }

    private SavedItineraryResponse toSavedResponse(SavedItineraryEntity entity) {
        List<SavedItineraryResponse.SavedStopResponse> stopResponses = entity.getStops().stream()
                .map(s -> new SavedItineraryResponse.SavedStopResponse(
                        s.getId(),
                        s.getPoiId(),
                        s.getVisitOrder(),
                        s.getStayDurationMinutes(),
                        s.getTravelTimeToNextMinutes(),
                        s.getDistanceToNextMeters()
                ))
                .toList();

        return new SavedItineraryResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getTotalDurationMinutes(),
                new GeoPointDto(entity.getStartLatitude(), entity.getStartLongitude()),
                entity.getTransportMode(),
                entity.getCreatedAt(),
                stopResponses
        );
    }
}
