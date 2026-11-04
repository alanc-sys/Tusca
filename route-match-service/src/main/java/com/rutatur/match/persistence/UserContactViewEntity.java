package com.rutatur.match.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Audit row appended on every successful contact reveal (RN-12). */
@Entity
@Table(name = "user_contact_view", schema = "match_schema")
public class UserContactViewEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "route_match_id", nullable = false, updatable = false)
    private UUID routeMatchId;

    @Column(name = "viewer_user_id", nullable = false, length = 255)
    private String viewerUserId;

    @Column(name = "viewed_user_id", nullable = false, length = 255)
    private String viewedUserId;

    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_info", nullable = false, columnDefinition = "jsonb")
    private JsonNode contactInfo;

    protected UserContactViewEntity() {
    }

    public UserContactViewEntity(
            UUID id,
            UUID routeMatchId,
            String viewerUserId,
            String viewedUserId,
            Instant revealedAt,
            JsonNode contactInfo) {
        this.id = id;
        this.routeMatchId = routeMatchId;
        this.viewerUserId = viewerUserId;
        this.viewedUserId = viewedUserId;
        this.revealedAt = revealedAt;
        this.contactInfo = contactInfo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRouteMatchId() {
        return routeMatchId;
    }

    public String getViewerUserId() {
        return viewerUserId;
    }

    public String getViewedUserId() {
        return viewedUserId;
    }

    public Instant getRevealedAt() {
        return revealedAt;
    }

    public JsonNode getContactInfo() {
        return contactInfo;
    }
}
