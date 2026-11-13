package com.rutatur.itinerary.controller;

import com.rutatur.itinerary.dto.SavedItineraryResponse;
import com.rutatur.itinerary.service.ItineraryService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint interno exclusivo para que route-match-service valide propiedad de ruta
 * y obtenga los POIs del itinerario sin acceder directamente a itinerary_db.
 */
@Hidden
@RestController
@RequestMapping("/internal/itineraries")
public class InternalItineraryController {

    private final ItineraryService itineraryService;

    public InternalItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping("/{id}")
    public SavedItineraryResponse getInternalItinerary(@PathVariable UUID id) {
        return itineraryService.getItineraryInternal(id);
    }
}
