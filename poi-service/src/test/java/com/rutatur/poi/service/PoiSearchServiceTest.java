package com.rutatur.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.cache.CacheKeyFactory;
import com.rutatur.poi.cache.PoiCacheEntity;
import com.rutatur.poi.cache.PoiCacheRepository;
import com.rutatur.poi.cache.PoiQueryCacheEntity;
import com.rutatur.poi.cache.PoiQueryCacheRepository;
import com.rutatur.poi.client.OverpassClient;
import com.rutatur.poi.config.PoiProperties;
import com.rutatur.poi.normalize.PoiNormalizer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoiSearchServiceTest {

    private static final String KEY = "query-key";
    private static final double LAT = -34.6037;
    private static final double LNG = -58.3816;
    private static final int RADIUS = 1000;
    private static final List<Category> CATEGORIES = List.of(Category.PARK);

    private final OverpassClient overpassClient = mock(OverpassClient.class);
    private final PoiNormalizer normalizer = mock(PoiNormalizer.class);
    private final CacheKeyFactory cacheKeyFactory = mock(CacheKeyFactory.class);
    private final PoiCacheRepository repository = mock(PoiCacheRepository.class);
    private final PoiQueryCacheRepository queryCacheRepository = mock(PoiQueryCacheRepository.class);
    private final PoiCacheWriter writer = mock(PoiCacheWriter.class);
    private final PoiProperties properties = new PoiProperties(
            null, Duration.ofMinutes(15), Duration.ofDays(7), null, null);
    private final PoiSearchService service = new PoiSearchService(
            overpassClient, normalizer, cacheKeyFactory, repository, queryCacheRepository,
            writer, properties);

    @BeforeEach
    void setUp() {
        when(cacheKeyFactory.key(LAT, LNG, RADIUS, CATEGORIES)).thenReturn(KEY);
        when(overpassClient.fetch(LAT, LNG, RADIUS, CATEGORIES))
                .thenReturn(new ObjectMapper().createObjectNode());
    }

    @Test
    void emptyProviderResultIsCachedSoTheNextSearchSkipsOverpass() {
        when(queryCacheRepository.findById(KEY)).thenReturn(Optional.empty());
        when(normalizer.normalize(any(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of());

        PoiSearchResponse first = service.search(LAT, LNG, RADIUS, CATEGORIES);

        assertThat(first.totalCount()).isZero();
        verify(writer).replace(eq(KEY), eq(List.of()), any(), any());

        when(queryCacheRepository.findById(KEY)).thenReturn(Optional.of(
                new PoiQueryCacheEntity(KEY, 0, Instant.now(), Instant.now().plusSeconds(900))));

        PoiSearchResponse second = service.search(LAT, LNG, RADIUS, CATEGORIES);

        assertThat(second.totalCount()).isZero();
        verify(overpassClient, times(1)).fetch(LAT, LNG, RADIUS, CATEGORIES);
    }

    @Test
    void freshNonEmptyCacheShortCircuitsOverpass() {
        PoiDto poi = poi("osm:node/1");
        when(queryCacheRepository.findById(KEY)).thenReturn(Optional.of(
                new PoiQueryCacheEntity(KEY, 1, Instant.now(), Instant.now().plusSeconds(900))));
        when(repository.findByQueryKey(KEY)).thenReturn(List.of(row(poi)));

        PoiSearchResponse response = service.search(LAT, LNG, RADIUS, CATEGORIES);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.pois().getFirst().id()).isEqualTo("osm:node/1");
        verify(overpassClient, times(0)).fetch(anyDouble(), anyDouble(), anyInt(), any());
    }

    @Test
    void staleRowsAreServedWhenTheProviderFails() {
        when(queryCacheRepository.findById(KEY)).thenReturn(Optional.empty());
        when(overpassClient.fetch(LAT, LNG, RADIUS, CATEGORIES))
                .thenThrow(new UpstreamServiceException(
                        ErrorCode.POI_PROVIDER_UNAVAILABLE, "down"));
        when(repository.findByQueryKey(KEY)).thenReturn(List.of(row(poi("osm:node/2"))));

        PoiSearchResponse response = service.search(LAT, LNG, RADIUS, CATEGORIES);

        assertThat(response.pois()).extracting(PoiDto::id).containsExactly("osm:node/2");
    }

    @Test
    void providerFailureWithoutCachePropagates() {
        when(queryCacheRepository.findById(KEY)).thenReturn(Optional.empty());
        when(overpassClient.fetch(LAT, LNG, RADIUS, CATEGORIES))
                .thenThrow(new UpstreamServiceException(
                        ErrorCode.POI_PROVIDER_UNAVAILABLE, "down"));
        when(repository.findByQueryKey(KEY)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.search(LAT, LNG, RADIUS, CATEGORIES))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.POI_PROVIDER_UNAVAILABLE);
    }

    private static PoiDto poi(String id) {
        return new PoiDto(
                id,
                "Park",
                Category.PARK,
                new com.rutatur.common.dto.GeoPointDto(LAT, LNG),
                "Somewhere",
                45,
                4.5,
                List.of());
    }

    private static PoiCacheEntity row(PoiDto poi) {
        Instant now = Instant.now();
        return new PoiCacheEntity(
                KEY,
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
                now.plusSeconds(900));
    }
}
