package com.rutatur.poi.controller;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.poi.service.PoiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pois")
@Tag(name = "POIs", description = "Búsqueda y consulta de Puntos de Interés turísticos")
public class PoiController {

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar y filtrar POIs por geolocalización, radio y categorías")
    public List<PoiDto> searchPois(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "2000") int radiusMeters,
            @RequestParam(required = false) List<String> categories
    ) {
        return poiService.searchPois(lat, lon, radiusMeters, categories);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar detalle de un POI específico por ID")
    public PoiDto getPoiById(@PathVariable String id) {
        return poiService.getPoiById(id);
    }
}
