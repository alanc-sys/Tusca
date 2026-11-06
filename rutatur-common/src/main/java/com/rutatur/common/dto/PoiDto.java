package com.rutatur.common.dto;

import com.rutatur.common.enums.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Normalized point of interest.
 *
 * @param id platform identifier in the form {@code osm:<node|way|relation>/<id>}
 */
public record PoiDto(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull Category category,
        @NotNull @Valid GeoPointDto location,
        String address,
        @Min(1) int visitTime,
        @DecimalMin("0") @DecimalMax("5") double rating,
        List<String> tags) {

    public PoiDto {
        if (tags == null) {
            tags = List.of();
        }
    }
}
