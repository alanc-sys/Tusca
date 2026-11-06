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

/** Internal candidate endpoint consumed by itinerary-service; same shape as public search. */
@RestController
@RequestMapping("/internal/pois")
public class InternalPoiController {

    private final PoiSearchService searchService;
    private final Validator validator;

    public InternalPoiController(PoiSearchService searchService, Validator validator) {
        this.searchService = searchService;
        this.validator = validator;
    }

    @GetMapping("/candidates")
    public PoiSearchResponse candidates(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam Integer radius,
            @RequestParam(required = false) String categories) {
        PoiSearchQuery query = new PoiSearchQuery(lat, lng, radius, CategoryParser.parse(categories));
        Set<ConstraintViolation<PoiSearchQuery>> violations = validator.validate(query);
        if (!violations.isEmpty()) {
            throw new InvalidRequestException("Invalid candidate query");
        }
        return searchService.search(query.lat(), query.lng(), query.radius(), query.categories());
    }
}
