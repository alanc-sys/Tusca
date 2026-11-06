package com.rutatur.poi.service;

import com.rutatur.poi.cache.PoiCacheEntity;
import com.rutatur.poi.cache.PoiCacheRepository;
import com.rutatur.poi.cache.PoiQueryCacheEntity;
import com.rutatur.poi.cache.PoiQueryCacheRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the delete-and-replace of one query key atomic without wrapping the Overpass call. */
@Component
public class PoiCacheWriter {

    private final PoiCacheRepository repository;
    private final PoiQueryCacheRepository queryCacheRepository;

    public PoiCacheWriter(
            PoiCacheRepository repository, PoiQueryCacheRepository queryCacheRepository) {
        this.repository = repository;
        this.queryCacheRepository = queryCacheRepository;
    }

    /**
     * Replaces the rows of one query key and records its result count, so an empty provider answer
     * is cached exactly like a non-empty one.
     */
    @Transactional
    public void replace(
            String queryKey, List<PoiCacheEntity> rows, Instant cachedAt, Instant expiresAt) {
        repository.deleteByQueryKeyValue(queryKey);
        repository.saveAll(rows);
        queryCacheRepository.save(
                new PoiQueryCacheEntity(queryKey, rows.size(), cachedAt, expiresAt));
    }
}
