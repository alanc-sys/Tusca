package com.rutatur.itinerary.web;

import com.rutatur.common.dto.InternalItineraryDto;
import com.rutatur.common.dto.InternalItinerarySummary;
import com.rutatur.itinerary.persistence.ItineraryPersistenceService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal snapshot endpoints consumed by route-match-service; guarded by the shared token. */
@RestController
@RequestMapping("/internal/itineraries")
public class InternalItineraryController {

    private final ItineraryPersistenceService persistenceService;

    public InternalItineraryController(ItineraryPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping("/{id}")
    public InternalItineraryDto detail(@PathVariable UUID id) {
        return persistenceService.internalDetail(id);
    }

    @GetMapping
    public List<InternalItinerarySummary> byOwner(@RequestParam("owner") String owner) {
        return persistenceService.internalSummaries(owner);
    }
}
