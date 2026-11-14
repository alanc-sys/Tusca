package com.rutatur.match.repository;

import com.rutatur.match.entity.SharedRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedRouteRepository extends JpaRepository<SharedRouteEntity, UUID> {

    List<SharedRouteEntity> findByUserIdAndActiveTrueOrderByCreatedAtDesc(String userId);

    List<SharedRouteEntity> findByActiveTrueAndUserIdNot(String userId);

    Optional<SharedRouteEntity> findByItineraryIdAndUserIdAndActiveTrue(UUID itineraryId, String userId);
}
