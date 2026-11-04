package com.rutatur.poi.service;

import com.rutatur.poi.cache.PoiCacheEntity;
import com.rutatur.poi.cache.PoiCacheRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the delete-and-replace of one query key atomic without wrapping the Overpass call. */
@Component
public class PoiCacheWriter {

    private final PoiCacheRepository repository;

    public PoiCacheWriter(PoiCacheRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replace(String queryKey, List<PoiCacheEntity> rows) {
        repository.deleteByQueryKeyValue(queryKey);
        repository.saveAll(rows);
    }
}
