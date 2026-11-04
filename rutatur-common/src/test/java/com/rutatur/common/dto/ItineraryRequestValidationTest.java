package com.rutatur.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.enums.Category;
import com.rutatur.common.enums.TransportMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItineraryRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsInclusiveBoundaries() {
        assertThat(validator.validate(request(30, 100, 1))).isEmpty();
        assertThat(validator.validate(request(480, 3000, 6))).isEmpty();
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThat(validator.validate(request(29, 100, 1))).isNotEmpty();
        assertThat(validator.validate(request(481, 100, 1))).isNotEmpty();
        assertThat(validator.validate(request(60, 99, 1))).isNotEmpty();
        assertThat(validator.validate(request(60, 3001, 1))).isNotEmpty();
        assertThat(validator.validate(request(60, 100, 0))).isNotEmpty();
    }

    @Test
    void rejectsEmptyPreferencesAndOutOfRangeCoordinates() {
        ItineraryRequestDto emptyPreferences = new ItineraryRequestDto(
                new GeoPointDto(-34.6, -58.4), 120, List.of(), 1000, TransportMode.WALKING, 3);
        ItineraryRequestDto badCoordinates = new ItineraryRequestDto(
                new GeoPointDto(91.0, -58.4), 120, List.of(Category.PARK), 1000, TransportMode.WALKING, 3);

        assertThat(validator.validate(emptyPreferences)).isNotEmpty();
        assertThat(validator.validate(badCoordinates)).isNotEmpty();
    }

    private static ItineraryRequestDto request(int availableTime, int radius, int maxPois) {
        return new ItineraryRequestDto(
                new GeoPointDto(-34.6, -58.4),
                availableTime,
                List.of(Category.MUSEUM, Category.PARK),
                radius,
                TransportMode.WALKING,
                maxPois);
    }
}
