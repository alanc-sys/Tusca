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
import com.rutatur.itinerary.entity.SavedItineraryEntity;
import com.rutatur.itinerary.repository.SavedItineraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryServiceTest {

    @Mock
    private PoiInternalClient poiInternalClient;

    @Mock
    private OsrmClient osrmClient;

    @Mock
    private SavedItineraryRepository itineraryRepository;

    private ItineraryService itineraryService;

    @BeforeEach
    void setUp() {
        itineraryService = new ItineraryService(poiInternalClient, osrmClient, itineraryRepository);
    }

    @Test
    void generatesSequentialRouteWithoutDuplicatesWithinAvailableTime() {
        PoiDto poi1 = new PoiDto("node-1", "Plaza San Martín", "park", new GeoPointDto(-31.4167, -64.1836), "Centro", 30);
        PoiDto poi2 = new PoiDto("node-2", "Cabildo", "museum", new GeoPointDto(-31.4165, -64.1841), "Centro", 60);
        // Duplicado intencional para verificar RN-02
        when(poiInternalClient.searchCandidatePois(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(poi1, poi1, poi2));

        int[][] durations = {
                {0, 5, 10},
                {5, 0, 3},
                {10, 3, 0}
        };
        double[][] distances = {
                {0, 300, 700},
                {300, 0, 150},
                {700, 150, 0}
        };
        when(osrmClient.fetchMatrix(any(), any()))
                .thenReturn(new OsrmClient.TravelMatrix(durations, distances, true));

        GenerateItineraryRequest request = new GenerateItineraryRequest(
                new GeoPointDto(-31.4170, -64.1830), 120, 1500, TransportMode.WALKING, List.of("park", "museum")
        );

        GeneratedItineraryResponse response = itineraryService.generateItinerary(request);

        assertEquals(2, response.route().size());
        assertEquals("node-1", response.route().get(0).poi().id());
        assertEquals("node-2", response.route().get(1).poi().id());
        assertTrue(response.totalDurationMinutes() <= 120);
    }

    @Test
    void throws422WhenAvailableTimeIsInsufficientForAnyPoi() {
        PoiDto museum = new PoiDto("node-2", "Cabildo", "museum", new GeoPointDto(-31.4165, -64.1841), "Centro", 60);
        when(poiInternalClient.searchCandidatePois(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(museum));
        when(osrmClient.fetchMatrix(any(), any()))
                .thenReturn(new OsrmClient.TravelMatrix(new int[][]{{0, 10}, {10, 0}}, new double[][]{{0, 500}, {500, 0}}, true));

        GenerateItineraryRequest request = new GenerateItineraryRequest(
                new GeoPointDto(-31.4170, -64.1830), 20, 1500, TransportMode.WALKING, null
        );

        RutaturException ex = assertThrows(RutaturException.class, () -> itineraryService.generateItinerary(request));
        assertEquals(ErrorCode.INSUFFICIENT_TIME, ex.getErrorCode());
    }

    @Test
    void osrmFallbackUsesHaversineWhenExternalServiceFails() {
        OsrmClient realFallbackClient = new OsrmClient("http://localhost:59999", 500);
        OsrmClient.TravelMatrix matrix = realFallbackClient.fallbackMatrix(
                List.of(new GeoPointDto(-31.4167, -64.1836), new GeoPointDto(-31.4285, -64.1795)),
                TransportMode.WALKING,
                new RuntimeException("OSRM down")
        );

        assertFalse(matrix.osrmUsed());
        assertTrue(matrix.distancesMeters()[0][1] > 1200);
        assertTrue(matrix.durationsMinutes()[0][1] > 10);
    }

    @Test
    void forbidsAccessToItineraryOwnedByAnotherUser() {
        UUID id = UUID.randomUUID();
        SavedItineraryEntity entity = new SavedItineraryEntity(
                "user-owner", "Mi Ruta", 90, -31.4167, -64.1836, TransportMode.WALKING, Instant.now()
        );
        when(itineraryRepository.findById(id)).thenReturn(Optional.of(entity));

        RutaturException ex = assertThrows(RutaturException.class, () -> itineraryService.getItinerary(id, "other-user"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }
}
