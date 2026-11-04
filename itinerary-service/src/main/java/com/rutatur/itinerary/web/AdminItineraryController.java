package com.rutatur.itinerary.web;

import com.rutatur.common.dto.SavedItinerarySummary;
import com.rutatur.itinerary.persistence.ItineraryPersistenceService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Minimal administrative read surface. */
@RestController
@RequestMapping("/api/admin/itineraries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminItineraryController {

    private final ItineraryPersistenceService persistenceService;

    public AdminItineraryController(ItineraryPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping
    public List<SavedItinerarySummary> byUser(@RequestParam("userId") String userId) {
        return persistenceService.adminList(userId);
    }
}
