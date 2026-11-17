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
import com.rutatur.itinerary.dto.SaveItineraryRequest;
import com.rutatur.itinerary.dto.SavedItineraryResponse;
import com.rutatur.itinerary.dto.UpdateItineraryRequest;
import com.rutatur.itinerary.entity.ItineraryPoiEntity;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void updatesOnlyProvidedFieldsOnPatch() {
        UUID id = UUID.randomUUID();
        SavedItineraryEntity entity = new SavedItineraryEntity(
                "user-owner", "Ruta Original", 90, -31.4167, -64.1836, TransportMode.WALKING, Instant.now()
        );
        entity.addStop(new ItineraryPoiEntity("node-1", 1, 30, 0, 0.0));

        when(itineraryRepository.findById(id)).thenReturn(Optional.of(entity));
        when(itineraryRepository.save(any(SavedItineraryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                "Ruta Renombrada", null, null, null, null
        );

        SavedItineraryResponse response = itineraryService.updateItinerary(id, "user-owner", request);

        assertEquals("Ruta Renombrada", response.name());
        assertEquals(90, response.totalDurationMinutes());
        assertEquals(TransportMode.WALKING, response.transportMode());
        assertEquals(-31.4167, response.startPoint().latitude());
        assertEquals(1, response.stops().size());
        assertEquals("node-1", response.stops().get(0).poiId());
        verify(itineraryRepository).save(entity);
    }

    @Test
    void replacesStopsAndUpdatesAllProvidedFieldsOnPatch() {
        UUID id = UUID.randomUUID();
        SavedItineraryEntity entity = new SavedItineraryEntity(
                "user-owner", "Ruta Original", 90, -31.4167, -64.1836, TransportMode.WALKING, Instant.now()
        );
        entity.addStop(new ItineraryPoiEntity("node-1", 1, 30, 0, 0.0));

        when(itineraryRepository.findById(id)).thenReturn(Optional.of(entity));
        when(itineraryRepository.save(any(SavedItineraryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                "Ruta en Auto",
                new GeoPointDto(-31.4200, -64.1880),
                TransportMode.DRIVING,
                110,
                List.of(
                        new SaveItineraryRequest.SaveItineraryStopRequest("node-2", 1, 45, 10, 1200.0),
                        new SaveItineraryRequest.SaveItineraryStopRequest("node-3", 2, 55, 0, 0.0)
                )
        );

        SavedItineraryResponse response = itineraryService.updateItinerary(id, "user-owner", request);

        assertEquals("Ruta en Auto", response.name());
        assertEquals(-31.4200, response.startPoint().latitude());
        assertEquals(-64.1880, response.startPoint().longitude());
        assertEquals(TransportMode.DRIVING, response.transportMode());
        assertEquals(110, response.totalDurationMinutes());
        assertEquals(2, response.stops().size());
        assertEquals("node-2", response.stops().get(0).poiId());
        assertEquals("node-3", response.stops().get(1).poiId());
    }

    @Test
    void rejectsEmptyPatchWithValidationError() {
        UUID id = UUID.randomUUID();
        UpdateItineraryRequest request = new UpdateItineraryRequest(null, null, null, null, null);

        RutaturException ex = assertThrows(
                RutaturException.class,
                () -> itineraryService.updateItinerary(id, "user-owner", request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verifyNoInteractions(itineraryRepository);
    }

    @Test
    void forbidsUpdatingItineraryOwnedByAnotherUser() {
        UUID id = UUID.randomUUID();
        SavedItineraryEntity entity = new SavedItineraryEntity(
                "user-owner", "Mi Ruta", 90, -31.4167, -64.1836, TransportMode.WALKING, Instant.now()
        );
        when(itineraryRepository.findById(id)).thenReturn(Optional.of(entity));

        UpdateItineraryRequest request = new UpdateItineraryRequest("Hack", null, null, null, null);

        RutaturException ex = assertThrows(
                RutaturException.class,
                () -> itineraryService.updateItinerary(id, "other-user", request)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void throws404WhenUpdatingNonExistentItinerary() {
        UUID id = UUID.randomUUID();
        when(itineraryRepository.findById(id)).thenReturn(Optional.empty());

        UpdateItineraryRequest request = new UpdateItineraryRequest("Nuevo", null, null, null, null);

        RutaturException ex = assertThrows(
                RutaturException.class,
                () -> itineraryService.updateItinerary(id, "user-owner", request)
        );
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }
}
