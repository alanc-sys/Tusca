package com.rutatur.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body para registrar la calificación de un match aceptado (RN-14).
 *
 * @param score   entero entre 1 y 5
 * @param comment comentario opcional de hasta 500 caracteres
 */
public record RateMatchRequest(
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 500) String comment) {
}
