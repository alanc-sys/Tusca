package com.rutatur.poi.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per query key so an empty provider result is cached as explicitly as a non-empty one.
 * Without it an area with no POIs would call Overpass on every search.
 */
@Entity
@Table(name = "poi_query_cache", schema = "poi_schema")
public class PoiQueryCacheEntity {

    @Id
    @Column(name = "query_key", nullable = false, length = 64)
    private String queryKey;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PoiQueryCacheEntity() {
    }

    public PoiQueryCacheEntity(String queryKey, int resultCount, Instant cachedAt, Instant expiresAt) {
        this.queryKey = queryKey;
        this.resultCount = resultCount;
        this.cachedAt = cachedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isFresh(Instant now) {
        return now.isBefore(expiresAt);
    }

    public boolean isEmpty() {
        return resultCount == 0;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public int getResultCount() {
        return resultCount;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
