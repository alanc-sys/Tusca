package com.rutatur.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.dto.GeoPointDto;
import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void identicalPointsReturnExactlyZero() {
        assertThat(GeoUtils.haversineMeters(-34.603722, -58.381592, -34.603722, -58.381592))
                .isEqualTo(0.0);
        assertThat(GeoUtils.haversineMeters(
                        new GeoPointDto(-34.603722, -58.381592),
                        new GeoPointDto(-34.603722, -58.381592)))
                .isEqualTo(0.0);
    }

    @Test
    void obeliscoToTeatroColonIsWithinOnePercent() {
        // Reference great-circle distance ~336 m.
        double meters = GeoUtils.haversineMeters(-34.603722, -58.381592, -34.601083, -58.383383);

        assertThat(meters).isBetween(332.0, 340.0);
    }

    @Test
    void dtoOverloadMatchesPrimitiveOverload() {
        GeoPointDto a = new GeoPointDto(-34.603722, -58.381592);
        GeoPointDto b = new GeoPointDto(-34.5838, -58.3927);

        assertThat(GeoUtils.haversineMeters(a, b))
                .isEqualTo(GeoUtils.haversineMeters(
                        a.latitude(), a.longitude(), b.latitude(), b.longitude()));
    }
}
