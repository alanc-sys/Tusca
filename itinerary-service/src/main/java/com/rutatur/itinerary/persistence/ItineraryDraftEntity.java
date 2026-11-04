package com.rutatur.itinerary.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Unowned generation draft; claimable once within the TTL. */
@Entity
@Table(name = "itinerary_draft")
public class ItineraryDraftEntity {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_data", nullable = false)
    private JsonNode requestData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_data", nullable = false)
    private JsonNode responseData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ItineraryDraftEntity() {
    }

    public ItineraryDraftEntity(UUID id, JsonNode requestData, JsonNode responseData, Instant createdAt) {
        this.id = id;
        this.requestData = requestData;
        this.responseData = responseData;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
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
}
