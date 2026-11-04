package com.rutatur.poi.geo;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.util.GeoUtils;
import java.util.List;

/** Radius containment helper, applied after normalization. */
public final class RadiusFilter {

    private RadiusFilter() {
    }

    public static List<PoiDto> within(List<PoiDto> pois, double lat, double lng, int radiusMeters) {
        return pois.stream()
                .filter(poi -> GeoUtils.haversineMeters(
                                lat, lng, poi.location().latitude(), poi.location().longitude())
                        <= radiusMeters)
                .toList();
    }
}
