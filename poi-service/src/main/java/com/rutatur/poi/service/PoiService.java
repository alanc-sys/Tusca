package com.rutatur.poi.service;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.common.util.GeoUtils;
import com.rutatur.poi.entity.PoiEntity;
import com.rutatur.poi.repository.PoiRepository;
import com.rutatur.poi.repository.PoiSearchAreaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquesta la búsqueda geográfica de POIs con caché por zona (stale-while-revalidate),
 * refresco asíncrono en segundo plano y ponderación por categorías (RN-04).
 */
@Service
public class PoiService {

    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 20_000;

    private final PoiRepository poiRepository;
    private final PoiSearchAreaRepository searchAreaRepository;
    private final PoiRefreshWorker poiRefreshWorker;
    private final Duration cacheTtl;

    public PoiService(
            PoiRepository poiRepository,
            PoiSearchAreaRepository searchAreaRepository,
            PoiRefreshWorker poiRefreshWorker,
            @Value("${poi.cache.ttl-hours:24}") long cacheTtlHours
    ) {
        this.poiRepository = poiRepository;
        this.searchAreaRepository = searchAreaRepository;
        this.poiRefreshWorker = poiRefreshWorker;
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
    }

    public List<PoiDto> searchPois(double lat, double lon, int radiusMeters, List<String> categories) {
        GeoUtils.validateCoordinates(lat, lon);
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new RutaturException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format("El radio debe estar entre %d y %d metros", MIN_RADIUS_METERS, MAX_RADIUS_METERS)
            );
        }

        double gridLat = roundToGrid(lat);
        double gridLon = roundToGrid(lon);

        List<PoiEntity> inRadius = findCachedWithinRadius(lat, lon, radiusMeters);
        if (!hasFreshAreaCache(gridLat, gridLon, radiusMeters)) {
            if (!inRadius.isEmpty()) {
                poiRefreshWorker.scheduleRefresh(lat, lon, gridLat, gridLon, radiusMeters);
            } else {
                poiRefreshWorker.refreshAreaSync(lat, lon, gridLat, gridLon, radiusMeters);
                inRadius = findCachedWithinRadius(lat, lon, radiusMeters);
            }
        }

        List<PoiEntity> prioritized = applyCategoryPreference(inRadius, categories, lat, lon);

        return prioritized.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PoiDto getPoiById(String id) {
        return poiRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "POI no encontrado con id: " + id
                ));
    }

    private boolean hasFreshAreaCache(double gridLat, double gridLon, int radiusMeters) {
        Instant cutoff = Instant.now().minus(cacheTtl);
        return searchAreaRepository
                .findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(
                        gridLat, gridLon, radiusMeters
                )
                .filter(area -> area.getFetchedAt().isAfter(cutoff))
                .isPresent();
    }

    private List<PoiEntity> findCachedWithinRadius(double lat, double lon, int radiusMeters) {
        return poiRepository.findAll().stream()
                .filter(poi -> GeoUtils.haversineDistanceMeters(lat, lon, poi.getLatitude(), poi.getLongitude()) <= radiusMeters)
                .toList();
    }

    /**
     * RN-04: Prioriza POIs cuyas categorías coincidan con las preferidas;
     * si no hay coincidencias en el radio, retorna POIs populares generales.
     */
    private List<PoiEntity> applyCategoryPreference(
            List<PoiEntity> candidates,
            List<String> categories,
            double originLat,
            double originLon
    ) {
        Comparator<PoiEntity> byDistance = Comparator.comparingDouble(
                poi -> GeoUtils.haversineDistanceMeters(originLat, originLon, poi.getLatitude(), poi.getLongitude())
        );

        if (categories == null || categories.isEmpty()) {
            return candidates.stream().sorted(byDistance).toList();
        }

        Set<String> normalized = categories.stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toSet());

        if (normalized.isEmpty()) {
            return candidates.stream().sorted(byDistance).toList();
        }

        List<PoiEntity> matching = candidates.stream()
                .filter(poi -> normalized.contains(poi.getCategory().toLowerCase(Locale.ROOT)))
                .sorted(byDistance)
                .toList();

        return matching.isEmpty() ? candidates.stream().sorted(byDistance).toList() : matching;
    }

    private double roundToGrid(double coordinate) {
        return Math.round(coordinate * 100.0) / 100.0;
    }

    private PoiDto toDto(PoiEntity entity) {
        return new PoiDto(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                new GeoPointDto(entity.getLatitude(), entity.getLongitude()),
                entity.getAddress(),
                entity.getEstimatedStayMinutes()
        );
    }
}
