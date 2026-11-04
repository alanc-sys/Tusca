package com.rutatur.match.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Opt-in publication of an itinerary. Contact data never lands here (RN-09, RN-12). */
@Entity
@Table(name = "shared_route")
public class SharedRouteEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "itinerary_id", nullable = false, updatable = false)
    private UUID itineraryId;

    @Column(name = "owner_user_id", nullable = false, length = 255)
    private String ownerUserId;

    @Column(name = "start_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal startLat;

    @Column(name = "start_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal startLng;

    @Column(name = "available_time", nullable = false)
    private int availableTime;

    @Column(name = "transport_mode", nullable = false, length = 16)
    private String transportMode;

    @Column(name = "total_time", nullable = false)
    private int totalTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "poi_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> poiIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categories", nullable = false, columnDefinition = "jsonb")
    private List<String> categories;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SharedRouteEntity() {
    }

    public SharedRouteEntity(
            UUID id,
            UUID itineraryId,
            String ownerUserId,
            BigDecimal startLat,
            BigDecimal startLng,
            int availableTime,
            String transportMode,
            int totalTime,
            List<String> poiIds,
            List<String> categories,
            boolean active,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.itineraryId = itineraryId;
        this.ownerUserId = ownerUserId;
        this.startLat = startLat;
        this.startLng = startLng;
        this.availableTime = availableTime;
        this.transportMode = transportMode;
        this.totalTime = totalTime;
        this.poiIds = List.copyOf(poiIds);
        this.categories = List.copyOf(categories);
        this.active = active;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void reactivate(Instant newExpiry) {
        this.active = true;
        this.expiresAt = newExpiry;
    }

    public boolean isUsable(Instant now) {
        return active && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public BigDecimal getStartLat() {
        return startLat;
    }

    public BigDecimal getStartLng() {
        return startLng;
    }

    public int getAvailableTime() {
        return availableTime;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public List<String> getPoiIds() {
        return poiIds;
    }

    public List<String> getCategories() {
        return categories;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
