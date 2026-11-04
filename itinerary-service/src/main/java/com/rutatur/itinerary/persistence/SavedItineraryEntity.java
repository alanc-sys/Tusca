package com.rutatur.itinerary.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Claimed itinerary owned by a JWT subject; keeps its full snapshot. */
@Entity
@Table(name = "saved_itinerary")
public class SavedItineraryEntity {

    @Id
    private UUID id;

    @Column(name = "itinerary_id", nullable = false, unique = true)
    private UUID itineraryId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

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

    @Column(name = "route_summary", nullable = false)
    private String routeSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_data", nullable = false)
    private JsonNode requestData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_data", nullable = false)
    private JsonNode responseData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedItineraryEntity() {
    }

    public SavedItineraryEntity(
            UUID id,
            UUID itineraryId,
            String userId,
            BigDecimal startLat,
            BigDecimal startLng,
            int availableTime,
            String transportMode,
            int totalTime,
            String routeSummary,
            JsonNode requestData,
            JsonNode responseData,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.itineraryId = itineraryId;
        this.userId = userId;
        this.startLat = startLat;
        this.startLng = startLng;
        this.availableTime = availableTime;
        this.transportMode = transportMode;
        this.totalTime = totalTime;
        this.routeSummary = routeSummary;
        this.requestData = requestData;
        this.responseData = responseData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public String getUserId() {
        return userId;
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

    public String getRouteSummary() {
        return routeSummary;
    }

    public JsonNode getRequestData() {
        return requestData;
    }

    public JsonNode getResponseData() {
        return responseData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
