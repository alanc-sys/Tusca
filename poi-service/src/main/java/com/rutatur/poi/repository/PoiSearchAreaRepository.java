package com.rutatur.poi.repository;

import com.rutatur.poi.entity.PoiSearchAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PoiSearchAreaRepository extends JpaRepository<PoiSearchAreaEntity, UUID> {

    Optional<PoiSearchAreaEntity> findTopByGridLatAndGridLonAndRadiusMetersGreaterThanEqualOrderByFetchedAtDesc(
            double gridLat,
            double gridLon,
            int radiusMeters
    );
}
