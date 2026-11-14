package com.rutatur.poi.service;

import com.rutatur.poi.client.OverpassClient;
import com.rutatur.poi.entity.PoiEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Ejecuta refrescos de Overpass API fuera del camino crítico HTTP (stale-while-revalidate)
 * con deduplicación de áreas en curso para evitar consultas duplicadas.
 */
@Service
public class PoiRefreshWorker {

    private static final Logger log = LoggerFactory.getLogger(PoiRefreshWorker.class);

    private record RefreshKey(double gridLat, double gridLon, int radiusMeters) {
    }

    private final OverpassClient overpassClient;
    private final PoiCacheWriter poiCacheWriter;
    private final Executor overpassRefreshExecutor;
    private final Set<RefreshKey> inFlight = ConcurrentHashMap.newKeySet();

    public PoiRefreshWorker(
            OverpassClient overpassClient,
            PoiCacheWriter poiCacheWriter,
            @Qualifier("overpassRefreshExecutor") Executor overpassRefreshExecutor
    ) {
        this.overpassClient = overpassClient;
        this.poiCacheWriter = poiCacheWriter;
        this.overpassRefreshExecutor = overpassRefreshExecutor;
    }

    public void scheduleRefresh(double lat, double lon, double gridLat, double gridLon, int radiusMeters) {
        RefreshKey key = new RefreshKey(gridLat, gridLon, radiusMeters);
        if (!inFlight.add(key)) {
            return;
        }

        try {
            overpassRefreshExecutor.execute(() -> {
                try {
                    refreshAreaSync(lat, lon, gridLat, gridLon, radiusMeters);
                } catch (Exception ex) {
                    log.warn("Fallo refresh asíncrono de Overpass para ({}, {}, r={}): {}",
                            gridLat, gridLon, radiusMeters, ex.getMessage());
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlight.remove(key);
            log.warn("Cola de refresh Overpass llena; omitiendo ({}, {}, r={})", gridLat, gridLon, radiusMeters);
        }
    }

    public void refreshAreaSync(double lat, double lon, double gridLat, double gridLon, int radiusMeters) {
        List<PoiEntity> fetched = overpassClient.fetchPois(lat, lon, radiusMeters);
        poiCacheWriter.saveAreaPois(fetched, gridLat, gridLon, radiusMeters);
    }
}
