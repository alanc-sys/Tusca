package com.rutatur.match.service;

import com.rutatur.common.dto.AdminRatingsReportResponse;
import com.rutatur.common.dto.UserRatingSummary;
import com.rutatur.common.dto.UserStatusResponse;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.entity.UserProfileEntity;
import com.rutatur.match.entity.UserProfileStatus;
import com.rutatur.match.repository.MatchRatingRepository;
import com.rutatur.match.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Informe de reputación y bloqueo/desbloqueo administrativo (RN-17, RN-18). */
@Service
public class AdminRatingsService {

    private static final BigDecimal RECOMMENDATION_THRESHOLD = new BigDecimal("2.5");
    private static final int MIN_RATINGS_TO_RECOMMEND = 2;

    private final UserProfileRepository userProfileRepository;
    private final MatchRatingRepository matchRatingRepository;

    public AdminRatingsService(
            UserProfileRepository userProfileRepository,
            MatchRatingRepository matchRatingRepository) {
        this.userProfileRepository = userProfileRepository;
        this.matchRatingRepository = matchRatingRepository;
    }

    @Transactional(readOnly = true)
    public AdminRatingsReportResponse report() {
        List<UserProfileEntity> profiles = userProfileRepository.findAll();

        List<UserRatingSummary> users = profiles.stream()
                // Primero quienes tienen calificaciones; dentro de cada grupo, peor promedio primero.
                .sorted(Comparator
                        .comparingInt((UserProfileEntity p) -> p.getRatingsCount() > 0 ? 0 : 1)
                        .thenComparing(UserProfileEntity::getAverageRating)
                        .thenComparing(UserProfileEntity::getUsername, Comparator.nullsLast(String::compareTo)))
                .map(AdminRatingsService::toSummary)
                .toList();

        long blockedUsersCount = profiles.stream()
                .filter(profile -> profile.getStatus() == UserProfileStatus.BLOCKED)
                .count();

        return new AdminRatingsReportResponse(
                matchRatingRepository.averageScore(),
                (int) matchRatingRepository.count(),
                (int) blockedUsersCount,
                users);
    }

    @Transactional
    public UserStatusResponse changeStatus(UUID userId, UserProfileStatus status) {
        UserProfileEntity profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.USER_PROFILE_NOT_FOUND, "No existe el perfil de usuario"));
        profile.setStatus(status);
        return new UserStatusResponse(profile.getId(), profile.getUsername(), profile.getStatus().name());
    }

    private static UserRatingSummary toSummary(UserProfileEntity profile) {
        boolean recommendedForBlock = profile.getRatingsCount() >= MIN_RATINGS_TO_RECOMMEND
                && profile.getAverageRating().compareTo(RECOMMENDATION_THRESHOLD) < 0;
        return new UserRatingSummary(
                profile.getId(),
                profile.getUsername(),
                profile.getStatus().name(),
                profile.getAverageRating().doubleValue(),
                profile.getRatingsCount(),
                recommendedForBlock);
    }
}
