package com.rutatur.poi.cache;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoiQueryCacheRepository extends JpaRepository<PoiQueryCacheEntity, String> {

    @Modifying
    @Query("delete from PoiQueryCacheEntity q where q.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
