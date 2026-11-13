package com.rutatur.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de áreas geográficas ya consultadas a Overpass para evitar caché incompleto
 * cuando cambian la ubicación o el radio de búsqueda.
 */
@Entity
@Table(name = "poi_search_areas")
public class PoiSearchAreaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grid_lat", nullable = false)
    private double gridLat;

    @Column(name = "grid_lon", nullable = false)
    private double gridLon;

    @Column(name = "radius_meters", nullable = false)
    private int radiusMeters;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PoiSearchAreaEntity() {
    }

    public PoiSearchAreaEntity(double gridLat, double gridLon, int radiusMeters, Instant fetchedAt) {
        this.gridLat = gridLat;
        this.gridLon = gridLon;
        this.radiusMeters = radiusMeters;
        this.fetchedAt = fetchedAt;
    }

    public UUID getId() {
        return id;
    }

    public double getGridLat() {
        return gridLat;
    }

    public double getGridLon() {
        return gridLon;
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
