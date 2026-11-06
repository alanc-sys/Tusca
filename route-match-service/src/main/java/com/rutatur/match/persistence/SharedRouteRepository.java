package com.rutatur.match.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Share lookups. Active means {@code is_active = true} and not past {@code expires_at}. */
public interface SharedRouteRepository extends JpaRepository<SharedRouteEntity, UUID> {

    Optional<SharedRouteEntity> findByItineraryId(UUID itineraryId);

    List<SharedRouteEntity> findByOwnerUserId(String ownerUserId);

    @Query("select s from SharedRouteEntity s where s.ownerUserId = :owner "
            + "and s.active = true and s.expiresAt > :now order by s.id asc")
    List<SharedRouteEntity> findActiveByOwner(@Param("owner") String owner, @Param("now") Instant now);

    @Query("select s from SharedRouteEntity s where s.ownerUserId <> :owner "
            + "and s.active = true and s.expiresAt > :now order by s.id asc")
    List<SharedRouteEntity> findActivePool(@Param("owner") String owner, @Param("now") Instant now);
}
