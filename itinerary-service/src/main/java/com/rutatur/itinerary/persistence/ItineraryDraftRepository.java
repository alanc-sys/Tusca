package com.rutatur.itinerary.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDraftRepository extends JpaRepository<ItineraryDraftEntity, UUID> {

    Optional<ItineraryDraftEntity> findByIdAndCreatedAtAfter(UUID id, Instant threshold);
}
