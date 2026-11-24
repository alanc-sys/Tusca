package com.rutatur.common.dto;

import java.util.UUID;

/**
 * Fila del informe de calificaciones (RN-18).
 *
 * @param recommendedForBlock {@code true} cuando {@code ratingsCount >= 2} y
 *                            {@code averageRating < 2.5}
 */
public record UserRatingSummary(
        UUID userId,
        String username,
        String status,
        double averageRating,
        int ratingsCount,
        boolean recommendedForBlock) {
}
