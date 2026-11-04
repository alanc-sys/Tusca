package com.rutatur.poi.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadiusFilterTest {

    @Test
    void excludesOutliersAndKeepsNearbyPois() {
        PoiDto near = poi("osm:node/1", -34.6000, -58.3800);
        PoiDto far = poi("osm:node/2", -34.6108, -58.3800);

        List<PoiDto> kept = RadiusFilter.within(List.of(near, far), -34.60, -58.38, 1000);

        assertThat(kept).extracting(PoiDto::id).containsExactly("osm:node/1");
    }

    private static PoiDto poi(String id, double lat, double lng) {
        return new PoiDto(id, "POI", Category.PARK, new GeoPointDto(lat, lng), null, 30, 0.0, List.of());
    }
}
