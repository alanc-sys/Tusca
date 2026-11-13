package com.rutatur.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "itinerary_pois")
public class ItineraryPoiEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private SavedItineraryEntity itinerary;

    /**
     * Referencia lógica al POI en poi_db (sin FK física entre microservicios).
     */
    @Column(name = "poi_id", nullable = false, length = 64)
    private String poiId;

    @Column(name = "visit_order", nullable = false)
    private int visitOrder;

    @Column(name = "stay_duration_minutes", nullable = false)
    private int stayDurationMinutes;

    @Column(name = "travel_time_to_next_minutes", nullable = false)
    private int travelTimeToNextMinutes;

    @Column(name = "distance_to_next_meters", nullable = false)
    private double distanceToNextMeters;

    protected ItineraryPoiEntity() {
    }

    public ItineraryPoiEntity(
            String poiId,
            int visitOrder,
            int stayDurationMinutes,
            int travelTimeToNextMinutes,
            double distanceToNextMeters
    ) {
        this.poiId = poiId;
        this.visitOrder = visitOrder;
        this.stayDurationMinutes = stayDurationMinutes;
        this.travelTimeToNextMinutes = travelTimeToNextMinutes;
        this.distanceToNextMeters = distanceToNextMeters;
    }

    void assignItinerary(SavedItineraryEntity itinerary) {
        this.itinerary = itinerary;
    }

    public UUID getId() {
        return id;
    }

    public String getPoiId() {
        return poiId;
    }

    public int getVisitOrder() {
        return visitOrder;
    }

    public int getStayDurationMinutes() {
        return stayDurationMinutes;
    }

    public int getTravelTimeToNextMinutes() {
        return travelTimeToNextMinutes;
    }

    public double getDistanceToNextMeters() {
        return distanceToNextMeters;
    }
}
