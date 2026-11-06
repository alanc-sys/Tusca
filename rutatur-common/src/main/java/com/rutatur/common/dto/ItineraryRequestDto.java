package com.rutatur.common.dto;

import com.rutatur.common.enums.Category;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Generation request. Generation radius is 100..3000 m and {@code maxPois} defaults to 6. */
public record ItineraryRequestDto(
        @NotNull @Valid GeoPointDto startPoint,
        @NotNull @Min(30) @Max(480) Integer availableTime,
        @NotEmpty List<Category> preferences,
        @NotNull @Min(100) @Max(3000) Integer radius,
        @NotNull TransportMode transportMode,
        @Min(1) Integer maxPois) {

    public static final int DEFAULT_MAX_POIS = 6;

    public int maxPoisOrDefault() {
        return maxPois == null ? DEFAULT_MAX_POIS : maxPois;
    }
}
