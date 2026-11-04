package com.rutatur.itinerary.web;

import com.rutatur.common.dto.SaveItineraryRequest;
import com.rutatur.common.dto.SavedItineraryDetail;
import com.rutatur.common.dto.SavedItineraryResponse;
import com.rutatur.common.dto.SavedItinerarySummary;
import com.rutatur.itinerary.persistence.ItineraryPersistenceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped itinerary history. The JWT subject is the only ownership source. */
@RestController
@RequestMapping("/api/itineraries")
@PreAuthorize("hasRole('USER')")
public class ItineraryController {

    private final ItineraryPersistenceService persistenceService;

    public ItineraryController(ItineraryPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @PostMapping
    public ResponseEntity<SavedItineraryResponse> save(
            @Valid @RequestBody SaveItineraryRequest request, @AuthenticationPrincipal Jwt jwt) {
        SavedItineraryResponse response = persistenceService.claim(request.itineraryId(), jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<SavedItinerarySummary> list(@AuthenticationPrincipal Jwt jwt) {
        return persistenceService.list(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public SavedItineraryDetail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return persistenceService.get(id, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        persistenceService.delete(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
