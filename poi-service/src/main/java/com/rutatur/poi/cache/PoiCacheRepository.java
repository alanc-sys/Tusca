package com.rutatur.poi.cache;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoiCacheRepository extends JpaRepository<PoiCacheEntity, PoiCacheId> {

    List<PoiCacheEntity> findByQueryKeyAndExpiresAtAfter(String queryKey, Instant now);

    List<PoiCacheEntity> findByQueryKey(String queryKey);

    @Modifying
    @Query("delete from PoiCacheEntity p where p.queryKey = :queryKey")
    int deleteByQueryKeyValue(@Param("queryKey") String queryKey);
}
