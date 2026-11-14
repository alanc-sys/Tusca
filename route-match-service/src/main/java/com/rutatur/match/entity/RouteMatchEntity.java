package com.rutatur.match.entity;

import com.rutatur.common.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "route_matches")
public class RouteMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "shared_route_a_id", nullable = false)
    private SharedRouteEntity sharedRouteA;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "shared_route_b_id", nullable = false)
    private SharedRouteEntity sharedRouteB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "similarity_score", nullable = false)
    private double similarityScore;

    @Column(name = "consent_a", nullable = false)
    private boolean consentA;

    @Column(name = "consent_b", nullable = false)
    private boolean consentB;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean involvesUser(String userId) {
        return sharedRouteA.getUserId().equals(userId) || sharedRouteB.getUserId().equals(userId);
    }

    public SharedRouteEntity getCounterpartyRoute(String userId) {
        if (sharedRouteA.getUserId().equals(userId)) {
            return sharedRouteB;
        }
        return sharedRouteA;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SharedRouteEntity getSharedRouteA() {
        return sharedRouteA;
    }

    public void setSharedRouteA(SharedRouteEntity sharedRouteA) {
        this.sharedRouteA = sharedRouteA;
    }

    public SharedRouteEntity getSharedRouteB() {
        return sharedRouteB;
    }

    public void setSharedRouteB(SharedRouteEntity sharedRouteB) {
        this.sharedRouteB = sharedRouteB;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public boolean isConsentA() {
        return consentA;
    }

    public void setConsentA(boolean consentA) {
        this.consentA = consentA;
    }

    public boolean isConsentB() {
        return consentB;
    }

    public void setConsentB(boolean consentB) {
        this.consentB = consentB;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
