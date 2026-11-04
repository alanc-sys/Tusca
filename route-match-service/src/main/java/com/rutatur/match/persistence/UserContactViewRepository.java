package com.rutatur.match.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Audit trail for contact reveals. */
public interface UserContactViewRepository extends JpaRepository<UserContactViewEntity, UUID> {

    List<UserContactViewEntity> findByRouteMatchId(UUID routeMatchId);

    long countByRouteMatchId(UUID routeMatchId);
}
