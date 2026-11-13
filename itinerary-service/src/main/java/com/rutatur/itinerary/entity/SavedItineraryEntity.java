package com.rutatur.itinerary.entity;

import com.rutatur.common.enums.TransportMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saved_itineraries")
public class SavedItineraryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "total_duration_minutes", nullable = false)
    private int totalDurationMinutes;

    @Column(name = "start_latitude", nullable = false)
    private double startLatitude;

    @Column(name = "start_longitude", nullable = false)
    private double startLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 32)
    private TransportMode transportMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("visitOrder ASC")
    private List<ItineraryPoiEntity> stops = new ArrayList<>();

    protected SavedItineraryEntity() {
    }

    public SavedItineraryEntity(
            String userId,
            String name,
            int totalDurationMinutes,
            double startLatitude,
            double startLongitude,
            TransportMode transportMode,
            Instant createdAt
    ) {
        this.userId = userId;
        this.name = name;
        this.totalDurationMinutes = totalDurationMinutes;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.transportMode = transportMode;
        this.createdAt = createdAt;
    }

    public void addStop(ItineraryPoiEntity stop) {
        stop.assignItinerary(this);
        this.stops.add(stop);
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public double getStartLatitude() {
        return startLatitude;
    }

    public double getStartLongitude() {
        return startLongitude;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public List<ItineraryPoiEntity> getStops() {
        return stops;
    }
}
