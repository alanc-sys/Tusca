package com.rutatur.itinerary.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedItineraryRepository extends JpaRepository<SavedItineraryEntity, UUID> {

    Optional<SavedItineraryEntity> findByIdAndUserId(UUID id, String userId);

    Optional<SavedItineraryEntity> findByItineraryId(UUID itineraryId);

    boolean existsByItineraryId(UUID itineraryId);

    List<SavedItineraryEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SavedItineraryEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
