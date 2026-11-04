package com.rutatur.match.persistence;

import com.rutatur.common.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Lifecycle row for a request between two shared routes (RN-10). */
@Entity
@Table(name = "route_match", schema = "match_schema")
public class RouteMatchEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requester_user_id", nullable = false, length = 255)
    private String requesterUserId;

    @Column(name = "responder_user_id", nullable = false, length = 255)
    private String responderUserId;

    @Column(name = "shared_route_a_id", nullable = false, updatable = false)
    private UUID sharedRouteAId;

    @Column(name = "shared_route_b_id", nullable = false, updatable = false)
    private UUID sharedRouteBId;

    @Column(name = "match_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "matched_poi_count", nullable = false)
    private int matchedPoiCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_pois", nullable = false, columnDefinition = "jsonb")
    private List<String> matchedPois;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MatchStatus status;

    @Column(name = "requester_accepted_at")
    private Instant requesterAcceptedAt;

    @Column(name = "responder_accepted_at")
    private Instant responderAcceptedAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected RouteMatchEntity() {
    }

    public RouteMatchEntity(
            UUID id,
            String requesterUserId,
            String responderUserId,
            UUID sharedRouteAId,
            UUID sharedRouteBId,
            BigDecimal matchScore,
            int matchedPoiCount,
            List<String> matchedPois,
            MatchStatus status,
            Instant requestedAt) {
        this.id = id;
        this.requesterUserId = requesterUserId;
        this.responderUserId = responderUserId;
        this.sharedRouteAId = sharedRouteAId;
        this.sharedRouteBId = sharedRouteBId;
        this.matchScore = matchScore;
        this.matchedPoiCount = matchedPoiCount;
        this.matchedPois = List.copyOf(matchedPois);
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public boolean isParticipant(String userId) {
        return requesterUserId.equals(userId) || responderUserId.equals(userId);
    }

    public String counterpartOf(String userId) {
        return requesterUserId.equals(userId) ? responderUserId : requesterUserId;
    }

    public boolean isRequester(String userId) {
        return requesterUserId.equals(userId);
    }

    /** Records one side's consent while {@code PENDING}; a repeated consent is a no-op. */
    public void recordConsent(boolean requester, Instant now) {
        if (status != MatchStatus.PENDING) {
            return;
        }
        if (requester) {
            if (requesterAcceptedAt == null) {
                requesterAcceptedAt = now;
            }
        } else if (responderAcceptedAt == null) {
            responderAcceptedAt = now;
        }
    }

    public boolean hasBothConsents() {
        return requesterAcceptedAt != null && responderAcceptedAt != null;
    }

    /** Terminal success: only reachable once both sides consented. */
    public void complete(Instant now) {
        if (status == MatchStatus.PENDING && hasBothConsents()) {
            status = MatchStatus.ACCEPTED;
            acceptedAt = now;
            respondedAt = now;
        }
    }

    public void reject(Instant now) {
        if (status == MatchStatus.PENDING) {
            status = MatchStatus.REJECTED;
            respondedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRequesterUserId() {
        return requesterUserId;
    }

    public String getResponderUserId() {
        return responderUserId;
    }

    public UUID getSharedRouteAId() {
        return sharedRouteAId;
    }

    public UUID getSharedRouteBId() {
        return sharedRouteBId;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public int getMatchedPoiCount() {
        return matchedPoiCount;
    }

    public List<String> getMatchedPois() {
        return matchedPois;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Instant getRequesterAcceptedAt() {
        return requesterAcceptedAt;
    }

    public Instant getResponderAcceptedAt() {
        return responderAcceptedAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
