package com.rutatur.poi.service;

import com.rutatur.poi.cache.PoiCacheRepository;
import com.rutatur.poi.cache.PoiQueryCacheRepository;
import com.rutatur.poi.config.PoiProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops cache rows that outlived {@code rutatur.poi.cache-retention}. Rows are kept well past their
 * TTL so they can still serve as a stale fallback when Overpass is down.
 */
@Component
public class PoiCacheCleanup {

    private static final Logger log = LoggerFactory.getLogger(PoiCacheCleanup.class);

    private final PoiCacheRepository repository;
    private final PoiQueryCacheRepository queryCacheRepository;
    private final PoiProperties properties;

    public PoiCacheCleanup(
            PoiCacheRepository repository,
            PoiQueryCacheRepository queryCacheRepository,
            PoiProperties properties) {
        this.repository = repository;
        this.queryCacheRepository = queryCacheRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.cacheRetention());
        int rows = repository.deleteExpiredBefore(cutoff);
        int markers = queryCacheRepository.deleteExpiredBefore(cutoff);
        if (rows > 0 || markers > 0) {
            log.info("Purged {} cached POIs and {} query markers past retention", rows, markers);
        }
    }
}
