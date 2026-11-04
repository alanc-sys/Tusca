package com.rutatur.itinerary.service;

import com.rutatur.common.dto.ItineraryRequestDto;
import com.rutatur.common.dto.ItineraryResponseDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.BusinessException;
import com.rutatur.itinerary.client.ItineraryPoiClient;
import com.rutatur.itinerary.client.OsrmTableClient;
import com.rutatur.itinerary.client.RoutingMatrix;
import com.rutatur.itinerary.optimize.ItineraryOptimizer;
import com.rutatur.itinerary.persistence.ItineraryPersistenceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Orchestrates candidates → ranking → routing matrix → greedy route → draft persistence. */
@Service
public class GenerationService {

    private final ItineraryPoiClient poiClient;
    private final OsrmTableClient osrmClient;
    private final ItineraryOptimizer optimizer;
    private final ItineraryPersistenceService persistenceService;

    public GenerationService(
            ItineraryPoiClient poiClient,
            OsrmTableClient osrmClient,
            ItineraryOptimizer optimizer,
            ItineraryPersistenceService persistenceService) {
        this.poiClient = poiClient;
        this.osrmClient = osrmClient;
        this.optimizer = optimizer;
        this.persistenceService = persistenceService;
    }

    public ItineraryResponseDto generate(ItineraryRequestDto request) {
        List<String> categories =
                request.preferences().stream().map(Enum::name).toList();
        PoiSearchResponse candidates = poiClient.candidates(
                request.startPoint().latitude(),
                request.startPoint().longitude(),
                request.radius(),
                categories);

        List<PoiDto> ordered = optimizer.rank(request, candidates.pois());
        if (ordered.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.ITINERARY_NOT_FEASIBLE, "No itinerary fits the available time");
        }

        RoutingMatrix matrix = osrmClient.table(request.transportMode(), request.startPoint(), ordered);
        UUID itineraryId = UUID.randomUUID();
        ItineraryResponseDto response = optimizer.buildRoute(itineraryId, request, ordered, matrix);
        persistenceService.saveDraft(request, response);
        return response;
    }
}
