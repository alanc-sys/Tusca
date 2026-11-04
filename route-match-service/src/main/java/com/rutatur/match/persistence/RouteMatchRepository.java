package com.rutatur.match.persistence;

import com.rutatur.common.enums.MatchStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Match reads; lifecycle writes take a row lock so consent can only be recorded once per side. */
public interface RouteMatchRepository extends JpaRepository<RouteMatchEntity, UUID> {

    Optional<RouteMatchEntity> findFirstByRequesterUserIdAndSharedRouteBIdAndStatus(
            String requesterUserId, UUID sharedRouteBId, MatchStatus status);

    List<RouteMatchEntity> findByRequesterUserIdOrResponderUserIdOrderByRequestedAtDesc(
            String requesterUserId, String responderUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from RouteMatchEntity m where m.id = :id")
    Optional<RouteMatchEntity> findByIdForUpdate(@Param("id") UUID id);
}
