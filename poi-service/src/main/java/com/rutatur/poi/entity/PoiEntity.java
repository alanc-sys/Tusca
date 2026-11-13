package com.rutatur.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Punto de Interés persistido en poi_db como caché enriquecida de Overpass API.
 */
@Entity
@Table(name = "pois")
public class PoiEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 255)
    private String address;

    @Column(name = "estimated_stay_minutes", nullable = false)
    private int estimatedStayMinutes;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    protected PoiEntity() {
    }

    public PoiEntity(
            String id,
            String name,
            String category,
            double latitude,
            double longitude,
            String address,
            int estimatedStayMinutes,
            Instant lastUpdated
    ) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.estimatedStayMinutes = estimatedStayMinutes;
        this.lastUpdated = lastUpdated;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public int getEstimatedStayMinutes() {
        return estimatedStayMinutes;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}
