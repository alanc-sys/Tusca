package com.rutatur.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Calificación mutua de un match aceptado (RN-14, RN-15). */
@Entity
@Table(
        name = "match_rating",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_rating_rater",
                columnNames = {"match_id", "rater_user_id"}))
public class MatchRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // La FK a route_matches se declara aquí: Hibernate (ddl-auto=update) la agrega cuando
    // crea route_matches, que no existe todavía durante la inicialización SQL de match_db.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private RouteMatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_user_id", nullable = false)
    private UserProfileEntity rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private UserProfileEntity rated;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public MatchRatingEntity() {
    }

    public MatchRatingEntity(
            RouteMatchEntity match, UserProfileEntity rater, UserProfileEntity rated,
            int score, String comment) {
        this.match = match;
        this.rater = rater;
        this.rated = rated;
        this.score = score;
        this.comment = comment;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RouteMatchEntity getMatch() {
        return match;
    }

    public void setMatch(RouteMatchEntity match) {
        this.match = match;
    }

    public UserProfileEntity getRater() {
        return rater;
    }

    public void setRater(UserProfileEntity rater) {
        this.rater = rater;
    }

    public UserProfileEntity getRated() {
        return rated;
    }

    public void setRated(UserProfileEntity rated) {
        this.rated = rated;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
