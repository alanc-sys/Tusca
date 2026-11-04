package com.rutatur.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.enums.Category;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoiSearchQueryValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void searchRadiusIsOneToFiveThousand() {
        assertThat(validator.validate(query(1))).isEmpty();
        assertThat(validator.validate(query(5000))).isEmpty();
        assertThat(validator.validate(query(0))).isNotEmpty();
        assertThat(validator.validate(query(5001))).isNotEmpty();
    }

    @Test
    void responseTotalCountMatchesListSize() {
        PoiSearchResponse response = PoiSearchResponse.of(List.of());

        assertThat(response.totalCount()).isZero();
        assertThat(response.pois()).isEmpty();
    }

    private static PoiSearchQuery query(int radius) {
        return new PoiSearchQuery(-34.6, -58.4, radius, List.of(Category.PARK));
    }
}
