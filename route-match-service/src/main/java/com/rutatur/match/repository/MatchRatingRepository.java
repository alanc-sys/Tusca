package com.rutatur.match.repository;

import com.rutatur.match.entity.MatchRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Persistencia de calificaciones; la unicidad por (match, calificador) se usa en W2 (RN-15). */
public interface MatchRatingRepository extends JpaRepository<MatchRatingEntity, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM MatchRatingEntity r
            WHERE r.match.id = :matchId AND r.rater.id = :raterUserId
            """)
    boolean existsByMatchAndRater(
            @Param("matchId") UUID matchId,
            @Param("raterUserId") UUID raterUserId);

    @Query("SELECT r FROM MatchRatingEntity r WHERE r.rated.id = :ratedUserId")
    List<MatchRatingEntity> findAllByRatedUserId(@Param("ratedUserId") UUID ratedUserId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM MatchRatingEntity r")
    double averageScore();
}
