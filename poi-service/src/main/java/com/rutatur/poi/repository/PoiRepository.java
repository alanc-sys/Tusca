package com.rutatur.poi.repository;

import com.rutatur.poi.entity.PoiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoiRepository extends JpaRepository<PoiEntity, String> {
}
