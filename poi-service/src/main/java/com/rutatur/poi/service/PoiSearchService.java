package com.rutatur.poi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.cache.CacheKeyFactory;
import com.rutatur.poi.cache.PoiCacheEntity;
import com.rutatur.poi.cache.PoiCacheRepository;
import com.rutatur.poi.client.OverpassClient;
import com.rutatur.poi.config.PoiProperties;
import com.rutatur.poi.normalize.PoiNormalizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Search pipeline: fresh cache → Overpass → normalize → persist → response, with stale cache as the
 * only degradation path. POIs are never fabricated.
 */
@Service
public class PoiSearchService {

    private final OverpassClient overpassClient;
    private final PoiNormalizer normalizer;
    private final CacheKeyFactory cacheKeyFactory;
    private final PoiCacheRepository repository;
    private final PoiCacheWriter writer;
    private final PoiProperties properties;

    public PoiSearchService(
            OverpassClient overpassClient,
            PoiNormalizer normalizer,
            CacheKeyFactory cacheKeyFactory,
            PoiCacheRepository repository,
            PoiCacheWriter writer,
            PoiProperties properties) {
        this.overpassClient = overpassClient;
        this.normalizer = normalizer;
        this.cacheKeyFactory = cacheKeyFactory;
        this.repository = repository;
        this.writer = writer;
        this.properties = properties;
    }

    public PoiSearchResponse search(double lat, double lng, int radius, List<Category> categories) {
        String key = cacheKeyFactory.key(lat, lng, radius, categories);
        List<PoiDto> fresh = toPois(repository.findByQueryKeyAndExpiresAtAfter(key, Instant.now()));
        if (!fresh.isEmpty()) {
            return PoiSearchResponse.of(fresh);
        }

        try {
            JsonNode payload = overpassClient.fetch(lat, lng, radius, categories);
            List<PoiDto> normalized = normalizer.normalize(payload, lat, lng, radius, categories);
            store(key, normalized);
            return PoiSearchResponse.of(normalized);
        } catch (UpstreamServiceException ex) {
            List<PoiDto> stale = toPois(repository.findByQueryKey(key));
            if (!stale.isEmpty()) {
                return PoiSearchResponse.of(stale);
            }
            throw ex;
        }
    }

    private void store(String key, List<PoiDto> pois) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.cacheTtl());
        List<PoiCacheEntity> rows = pois.stream()
                .map(poi -> new PoiCacheEntity(
                        key,
                        poi.id(),
                        poi.name(),
                        poi.category().name(),
                        BigDecimal.valueOf(poi.location().latitude()),
                        BigDecimal.valueOf(poi.location().longitude()),
                        poi.visitTime(),
                        BigDecimal.valueOf(poi.rating()),
                        poi.address(),
                        poi.tags(),
                        now,
                        expiresAt))
                .toList();
        writer.replace(key, rows);
    }

    private static List<PoiDto> toPois(List<PoiCacheEntity> rows) {
        return rows.stream()
                .sorted((a, b) -> a.getExternalId().compareTo(b.getExternalId()))
                .map(row -> new PoiDto(
                        row.getExternalId(),
                        row.getName(),
                        Category.valueOf(row.getCategory()),
                        new com.rutatur.common.dto.GeoPointDto(
                                row.getLat().doubleValue(), row.getLng().doubleValue()),
                        row.getAddress(),
                        row.getVisitTime(),
                        row.getRating().doubleValue(),
                        row.getTags()))
                .toList();
    }

    /** Exposed for the 503 path so the message never leaks provider details. */
    public static UpstreamServiceException providerUnavailable() {
        return new UpstreamServiceException(
                ErrorCode.POI_PROVIDER_UNAVAILABLE, "POI provider is unavailable");
    }
}
