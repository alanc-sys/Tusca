package com.rutatur.common.dto;

import java.util.List;

/** POI search result envelope; {@code totalCount} always equals {@code pois.size()}. */
public record PoiSearchResponse(List<PoiDto> pois, int totalCount) {

    public PoiSearchResponse {
        if (pois == null) {
            pois = List.of();
        }
    }

    public static PoiSearchResponse of(List<PoiDto> pois) {
        List<PoiDto> safe = pois == null ? List.of() : List.copyOf(pois);
        return new PoiSearchResponse(safe, safe.size());
    }
}
