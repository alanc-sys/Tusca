package com.rutatur.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeStatusTest {

    @Test
    void everyCodeCarriesItsDocumentedStatus() {
        assertThat(ErrorCode.VALIDATION_FAILED.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.INTERNAL_AUTH_REQUIRED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.MATCH_ACCESS_DENIED.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.CONTACT_NOT_REVEALED.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.ITINERARY_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.SHARE_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.MATCH_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.MATCH_TRANSITION_INVALID.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.ITINERARY_ALREADY_SAVED.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.ITINERARY_NOT_FEASIBLE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.NO_ACTIVE_SHARE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.POI_PROVIDER_UNAVAILABLE.httpStatus()).isEqualTo(503);
        assertThat(ErrorCode.CONTACT_PROVIDER_UNAVAILABLE.httpStatus()).isEqualTo(503);
    }

    @Test
    void violationsAreOnlyPresentForValidationErrors() {
        assertThat(ApiError.of(ErrorCode.ITINERARY_NOT_FOUND, "missing", "/x").violations()).isNull();
        assertThat(ApiError.validation("bad", "/x", java.util.List.of()).violations()).isNotNull();
    }
}
