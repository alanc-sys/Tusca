package com.rutatur.poi.service;

import com.rutatur.poi.entity.PoiEntity;
import com.rutatur.poi.entity.PoiSearchAreaEntity;
import com.rutatur.poi.repository.PoiRepository;
import com.rutatur.poi.repository.PoiSearchAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Persiste en una transacción corta los POIs obtenidos desde Overpass y el registro del área consultada.
 */
@Service
public class PoiCacheWriter {

    private final PoiRepository poiRepository;
    private final PoiSearchAreaRepository searchAreaRepository;

    public PoiCacheWriter(PoiRepository poiRepository, PoiSearchAreaRepository searchAreaRepository) {
        this.poiRepository = poiRepository;
        this.searchAreaRepository = searchAreaRepository;
    }

    @Transactional
    public void saveAreaPois(List<PoiEntity> fetched, double gridLat, double gridLon, int radiusMeters) {
        if (!fetched.isEmpty()) {
            poiRepository.saveAll(fetched);
        }
        searchAreaRepository.save(new PoiSearchAreaEntity(gridLat, gridLon, radiusMeters, Instant.now()));
    }
}
