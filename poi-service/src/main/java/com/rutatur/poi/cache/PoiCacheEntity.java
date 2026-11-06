package com.rutatur.poi.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cached normalized POI for a query key, with its own expiry instant. */
@Entity
@Table(name = "poi_cache")
@IdClass(PoiCacheId.class)
public class PoiCacheEntity {

    @Id
    @Column(name = "query_key", nullable = false, length = 64)
    private String queryKey;

    @Id
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "visit_time", nullable = false)
    private int visitTime;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    private String address;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PoiCacheEntity() {
    }

    public PoiCacheEntity(
            String queryKey,
            String externalId,
            String name,
            String category,
            BigDecimal lat,
            BigDecimal lng,
            int visitTime,
            BigDecimal rating,
            String address,
            List<String> tags,
            Instant cachedAt,
            Instant expiresAt) {
        this.queryKey = queryKey;
        this.externalId = externalId;
        this.name = name;
        this.category = category;
        this.lat = lat;
        this.lng = lng;
        this.visitTime = visitTime;
        this.rating = rating;
        this.address = address;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.cachedAt = cachedAt;
        this.expiresAt = expiresAt;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public int getVisitTime() {
        return visitTime;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getAddress() {
        return address;
    }

    public List<String> getTags() {
        return tags;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
