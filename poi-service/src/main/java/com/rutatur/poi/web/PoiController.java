package com.rutatur.poi.web;

import com.rutatur.common.dto.PoiSearchQuery;
import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.exception.InvalidRequestException;
import com.rutatur.poi.service.PoiSearchService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public POI search. */
@RestController
@RequestMapping("/api/pois")
public class PoiController {

    private final PoiSearchService searchService;
    private final Validator validator;

    public PoiController(PoiSearchService searchService, Validator validator) {
        this.searchService = searchService;
        this.validator = validator;
    }

    @GetMapping("/search")
    public PoiSearchResponse search(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam Integer radius,
            @RequestParam(required = false) String categories) {
        PoiSearchQuery query = new PoiSearchQuery(lat, lng, radius, CategoryParser.parse(categories));
        validate(query);
        return searchService.search(query.lat(), query.lng(), query.radius(), query.categories());
    }

    private void validate(PoiSearchQuery query) {
        Set<ConstraintViolation<PoiSearchQuery>> violations = validator.validate(query);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Invalid search query");
            throw new InvalidRequestException(message);
        }
    }
}
