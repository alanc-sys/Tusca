package com.rutatur.poi.service;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
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
    private PoiRefreshWorker poiRefreshWorker;

    private PoiService poiService;

    @BeforeEach
    void setUp() {
        poiService = new PoiService(poiRepository, searchAreaRepository, poiRefreshWorker, 24);
    }

    @Test
    void returnsCachedPoisWithoutSchedulingRefreshWhenAreaIsFresh() {
        PoiEntity museum = new PoiEntity("node-1", "Museo Genaro Pérez", "museum", -31.4167, -64.1836, "Av. General Paz 33", 60, Instant.now());
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(-31.42, -64.18, 1000))
                .thenReturn(Optional.of(new PoiSearchAreaEntity(-31.42, -64.18, 2000, Instant.now())));
        when(poiRepository.findAll()).thenReturn(List.of(museum));

        List<PoiDto> result = poiService.searchPois(-31.4167, -64.1836, 1000, List.of("museum"));

        assertEquals(1, result.size());
        assertEquals("Museo Genaro Pérez", result.get(0).name());
        verify(poiRefreshWorker, never()).scheduleRefresh(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(poiRefreshWorker, never()).refreshAreaSync(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void returnsStaleCacheImmediatelyAndSchedulesAsyncRefresh() {
        PoiEntity park = new PoiEntity("node-2", "Parque Sarmiento", "park", -31.4285, -64.1795, "Deodoro Roca", 45, Instant.now());
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());
        when(poiRepository.findAll()).thenReturn(List.of(park));

        List<PoiDto> result = poiService.searchPois(-31.4285, -64.1795, 1500, null);

        assertEquals(1, result.size());
        assertEquals("Parque Sarmiento", result.get(0).name());
        verify(poiRefreshWorker).scheduleRefresh(-31.4285, -64.1795, -31.43, -64.18, 1500);
        verify(poiRefreshWorker, never()).refreshAreaSync(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void refreshesSynchronouslyWhenNoCacheExistsAndReturnsPopulatedPois() {
        PoiEntity museum = new PoiEntity("node-10", "Cabildo", "museum", -31.4167, -64.1836, "Independencia 30", 60, Instant.now());
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());
        when(poiRepository.findAll()).thenReturn(List.of(), List.of(museum));

        List<PoiDto> result = poiService.searchPois(-31.4167, -64.1836, 1500, null);

        assertEquals(1, result.size());
        assertEquals("Cabildo", result.get(0).name());
        verify(poiRefreshWorker).refreshAreaSync(-31.4167, -64.1836, -31.42, -64.18, 1500);
    }

    @Test
    void throws503WhenOverpassFailsAndNoCacheExists() {
        when(searchAreaRepository.findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());
        when(poiRepository.findAll()).thenReturn(List.of());
        doThrow(new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Overpass down"))
                .when(poiRefreshWorker).refreshAreaSync(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());

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
        verify(poiRefreshWorker, never()).scheduleRefresh(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }
}
