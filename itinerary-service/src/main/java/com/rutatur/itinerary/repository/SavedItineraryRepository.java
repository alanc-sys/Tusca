package com.rutatur.itinerary.repository;

import com.rutatur.itinerary.entity.SavedItineraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedItineraryRepository extends JpaRepository<SavedItineraryEntity, UUID> {

    List<SavedItineraryEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
