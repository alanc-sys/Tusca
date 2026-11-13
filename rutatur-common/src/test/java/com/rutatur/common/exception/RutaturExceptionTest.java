package com.rutatur.common.exception;

import com.rutatur.common.dto.ApiError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RutaturExceptionTest {

    @Test
    void buildsApiErrorFromExceptionAndErrorCode() {
        RutaturException ex = new RutaturException(
                ErrorCode.CONSENT_REQUIRED,
                "Falta aceptación bilateral",
                List.of("matchStatus=PENDING")
        );

        ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), "/api/routes/matches/123/contact", ex.getDetails());

        assertNotNull(error.timestamp());
        assertEquals(403, error.status());
        assertEquals("CONSENT_REQUIRED", error.code());
        assertEquals("Falta aceptación bilateral", error.message());
        assertEquals("/api/routes/matches/123/contact", error.path());
        assertEquals(List.of("matchStatus=PENDING"), error.details());
    }
}
