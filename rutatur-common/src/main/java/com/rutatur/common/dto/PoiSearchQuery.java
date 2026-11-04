package com.rutatur.common.dto;

import com.rutatur.common.enums.Category;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * POI search query. The search extent is 1..5000 m, distinct from the generation radius.
 */
public record PoiSearchQuery(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotNull @Min(1) @Max(5000) Integer radius,
        List<Category> categories) {

    public PoiSearchQuery {
        if (categories == null) {
            categories = List.of();
        }
    }
}
