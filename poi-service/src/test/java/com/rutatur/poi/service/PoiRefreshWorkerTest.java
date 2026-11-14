package com.rutatur.poi.service;

import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.poi.client.OverpassClient;
import com.rutatur.poi.entity.PoiEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiRefreshWorkerTest {

    @Mock
    private OverpassClient overpassClient;

    @Mock
    private PoiCacheWriter poiCacheWriter;

    @Test
    void deduplicatesConcurrentAsyncRefreshesForSameGridArea() throws Exception {
        CountDownLatch enteredFetch = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        PoiEntity poi = new PoiEntity("node-1", "Museo", "museum", -31.4167, -64.1836, "Centro", 60, Instant.now());

        when(overpassClient.fetchPois(-31.4167, -64.1836, 2000)).thenAnswer(inv -> {
            enteredFetch.countDown();
            releaseFetch.await(5, TimeUnit.SECONDS);
            return List.of(poi);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            PoiRefreshWorker worker = new PoiRefreshWorker(overpassClient, poiCacheWriter, executor);

            worker.scheduleRefresh(-31.4167, -64.1836, -31.42, -64.18, 2000);
            assertTrue(enteredFetch.await(2, TimeUnit.SECONDS));

            // Segundo disparo para la misma área mientras el primero está en curso
            worker.scheduleRefresh(-31.4167, -64.1836, -31.42, -64.18, 2000);

            releaseFetch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            verify(overpassClient, times(1)).fetchPois(-31.4167, -64.1836, 2000);
            verify(poiCacheWriter, times(1)).saveAreaPois(List.of(poi), -31.42, -64.18, 2000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releasesInFlightLockWhenAsyncRefreshFails() {
        Executor directExecutor = Runnable::run;
        PoiRefreshWorker worker = new PoiRefreshWorker(overpassClient, poiCacheWriter, directExecutor);

        when(overpassClient.fetchPois(-31.4167, -64.1836, 2000))
                .thenThrow(new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Timeout"))
                .thenReturn(List.of());

        worker.scheduleRefresh(-31.4167, -64.1836, -31.42, -64.18, 2000);
        worker.scheduleRefresh(-31.4167, -64.1836, -31.42, -64.18, 2000);

        verify(overpassClient, times(2)).fetchPois(-31.4167, -64.1836, 2000);
        verify(poiCacheWriter, times(1)).saveAreaPois(List.of(), -31.42, -64.18, 2000);
    }

    @Test
    void refreshAreaSyncPropagatesExceptionWithoutPersisting() {
        Executor directExecutor = Runnable::run;
        PoiRefreshWorker worker = new PoiRefreshWorker(overpassClient, poiCacheWriter, directExecutor);

        when(overpassClient.fetchPois(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Down"));

        RutaturException ex = assertThrows(RutaturException.class,
                () -> worker.refreshAreaSync(-31.4167, -64.1836, -31.42, -64.18, 2000));

        assertEquals(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex.getErrorCode());
        verify(poiCacheWriter, never()).saveAreaPois(any(), anyDouble(), anyDouble(), anyInt());
    }
}
