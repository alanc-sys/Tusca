package com.rutatur.poi.service;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.poi.client.OverpassClient;
import com.rutatur.poi.entity.PoiEntity;
import com.rutatur.poi.entity.PoiSearchAreaEntity;
import com.rutatur.poi.repository.PoiRepository;
import com.rutatur.poi.repository.PoiSearchAreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiServiceTest {

    @Mock
    private PoiRepository poiRepository;

    @Mock
    private PoiSearchAreaRepository searchAreaRepository;

    @Mock
    private OverpassClient overpassClient;

    private PoiService poiService;

    @BeforeEach
    void setUp() {
        poiService = new PoiService(poiRepository, searchAreaRepository, overpassClient, 24);
    }

    @Test
    void returnsCachedPoisWithoutCallingOverpassWhenAreaIsFresh() {
        PoiEntity museum = new PoiEntity("node-1", "Museo Genaro Pérez", "museum", -31.4167, -64.1836, "Av. General Paz 33", 60, Instant.now());
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(-31.42, -64.18, 1000))
                .thenReturn(Optional.of(new PoiSearchAreaEntity(-31.42, -64.18, 2000, Instant.now())));
        when(poiRepository.findAll()).thenReturn(List.of(museum));

        List<PoiDto> result = poiService.searchPois(-31.4167, -64.1836, 1000, List.of("museum"));

        assertEquals(1, result.size());
        assertEquals("Museo Genaro Pérez", result.get(0).name());
        verify(overpassClient, never()).fetchPois(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void fallsBackToStaleCacheWhenOverpassFails() {
        PoiEntity park = new PoiEntity("node-2", "Parque Sarmiento", "park", -31.4285, -64.1795, "Deodoro Roca", 45, Instant.now().minus(48, ChronoUnit.HOURS));
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());
        when(overpassClient.fetchPois(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Overpass timeout"));
        when(poiRepository.findAll()).thenReturn(List.of(park));

        List<PoiDto> result = poiService.searchPois(-31.4285, -64.1795, 1500, null);

        assertEquals(1, result.size());
        assertEquals("Parque Sarmiento", result.get(0).name());
    }

    @Test
    void throws503WhenOverpassFailsAndNoCacheExists() {
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());
        when(overpassClient.fetchPois(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Overpass down"));
        when(poiRepository.findAll()).thenReturn(List.of());

        RutaturException ex = assertThrows(RutaturException.class,
                () -> poiService.searchPois(-31.4167, -64.1836, 1500, null));

        assertEquals(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void fallsBackToGeneralPopularPoisWhenPreferredCategoryNotFoundInRadius() {
        PoiEntity cafe = new PoiEntity("node-3", "Café Central", "cafe", -31.4167, -64.1836, "Centro", 30, Instant.now());
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(new PoiSearchAreaEntity(-31.42, -64.18, 2000, Instant.now())));
        when(poiRepository.findAll()).thenReturn(List.of(cafe));

        List<PoiDto> result = poiService.searchPois(-31.4167, -64.1836, 1500, List.of("museum"));

        assertEquals(1, result.size());
        assertEquals("cafe", result.get(0).category());
        verify(poiRepository, never()).saveAll(any());
    }
}
