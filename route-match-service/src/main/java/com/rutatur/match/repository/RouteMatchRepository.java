package com.rutatur.match.repository;

import com.rutatur.common.enums.MatchStatus;
import com.rutatur.match.entity.RouteMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RouteMatchRepository extends JpaRepository<RouteMatchEntity, UUID> {

    @Query("""
            SELECT m FROM RouteMatchEntity m
            WHERE m.sharedRouteA.userId = :userId OR m.sharedRouteB.userId = :userId
            ORDER BY m.updatedAt DESC
            """)
    List<RouteMatchEntity> findAllByParticipant(@Param("userId") String userId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM RouteMatchEntity m
            WHERE m.status IN :statuses
              AND ((m.sharedRouteA.id = :routeA AND m.sharedRouteB.id = :routeB)
                OR (m.sharedRouteA.id = :routeB AND m.sharedRouteB.id = :routeA))
            """)
    boolean existsActiveMatchBetween(
            @Param("routeA") UUID routeA,
            @Param("routeB") UUID routeB,
            @Param("statuses") Collection<MatchStatus> statuses);
}
