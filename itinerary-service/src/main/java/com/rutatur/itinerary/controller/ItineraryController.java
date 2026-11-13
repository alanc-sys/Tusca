package com.rutatur.itinerary.controller;

import com.rutatur.itinerary.dto.GenerateItineraryRequest;
import com.rutatur.itinerary.dto.GeneratedItineraryResponse;
import com.rutatur.itinerary.dto.SaveItineraryRequest;
import com.rutatur.itinerary.dto.SavedItineraryResponse;
import com.rutatur.itinerary.service.ItineraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Itineraries", description = "Generación optimizada y gestión de itinerarios turísticos")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @PostMapping("/api/itinerary/generate")
    @Operation(summary = "Generar itinerario borrador optimizado con tiempos reales de OSRM")
    public GeneratedItineraryResponse generateItinerary(@Valid @RequestBody GenerateItineraryRequest request) {
        return itineraryService.generateItinerary(request);
    }

    @PostMapping("/api/itineraries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Guardar itinerario en el historial del usuario autenticado")
    public SavedItineraryResponse saveItinerary(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveItineraryRequest request
    ) {
        return itineraryService.saveItinerary(jwt.getSubject(), request);
    }

    @GetMapping("/api/itineraries")
    @Operation(summary = "Listar historial de itinerarios del usuario autenticado")
    public List<SavedItineraryResponse> listUserItineraries(@AuthenticationPrincipal Jwt jwt) {
        return itineraryService.listUserItineraries(jwt.getSubject());
    }

    @GetMapping("/api/itineraries/{id}")
    @Operation(summary = "Consultar detalle de un itinerario propio")
    public SavedItineraryResponse getItinerary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return itineraryService.getItinerary(id, jwt.getSubject());
    }

    @DeleteMapping("/api/itineraries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar permanentemente un itinerario propio")
    public void deleteItinerary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        itineraryService.deleteItinerary(id, jwt.getSubject());
    }
}
