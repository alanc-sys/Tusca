package com.rutatur.common.dto;

import java.util.List;

/** Métricas globales y detalle por usuario del informe de calificaciones (RN-18). */
public record AdminRatingsReportResponse(
        double platformAverageRating,
        int totalRatings,
        int blockedUsersCount,
        List<UserRatingSummary> users) {

    public AdminRatingsReportResponse {
        users = users == null ? List.of() : List.copyOf(users);
    }
}
