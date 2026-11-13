package com.rutatur.poi.controller;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.poi.service.PoiService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint interno exclusivo para consumo servicio-a-servicio (itinerary-service),
 * protegido por {@link com.rutatur.poi.security.InternalTokenFilter} (RN-10).
 */
@Hidden
@RestController
@RequestMapping("/internal/pois")
public class InternalPoiController {

    private final PoiService poiService;

    public InternalPoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping("/search")
    public List<PoiDto> searchInternal(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "2000") int radiusMeters,
            @RequestParam(required = false) List<String> categories
    ) {
        return poiService.searchPois(lat, lon, radiusMeters, categories);
    }
}
