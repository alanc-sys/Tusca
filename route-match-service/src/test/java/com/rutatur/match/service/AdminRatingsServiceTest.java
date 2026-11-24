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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRatingsServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private MatchRatingRepository matchRatingRepository;

    private AdminRatingsService service() {
        return new AdminRatingsService(userProfileRepository, matchRatingRepository);
    }

    @Test
    void reportSortsRatedUsersFirstByAverageAscendingAndFlagsRecommendations() {
        UserProfileEntity lowOneVote = profile("low-one", 1, "1.00", UserProfileStatus.ACTIVE);
        UserProfileEntity badRated = profile("bad", 2, "2.00", UserProfileStatus.ACTIVE);
        UserProfileEntity goodRated = profile("good", 3, "4.50", UserProfileStatus.ACTIVE);
        UserProfileEntity blockedNoRatings = profile("aaa-blocked", 0, "0.00", UserProfileStatus.BLOCKED);
        UserProfileEntity activeNoRatings = profile("zzz-active", 0, "0.00", UserProfileStatus.ACTIVE);
        UserProfileEntity admin = profile("admin1", 0, "0.00", UserProfileStatus.BLOCKED);
        admin.setAdmin(true);

        when(userProfileRepository.findAll()).thenReturn(List.of(
                goodRated, activeNoRatings, badRated, blockedNoRatings, lowOneVote, admin));
        when(matchRatingRepository.averageScore()).thenReturn(2.75);
        when(matchRatingRepository.count()).thenReturn(6L);

        AdminRatingsReportResponse report = service().report();

        assertThat(report.platformAverageRating()).isEqualTo(2.75);
        assertThat(report.totalRatings()).isEqualTo(6);
        assertThat(report.blockedUsersCount()).isEqualTo(1);
        assertThat(report.users()).extracting(UserRatingSummary::username)
                .containsExactly("low-one", "bad", "good", "aaa-blocked", "zzz-active");
        assertThat(report.users()).extracting(UserRatingSummary::recommendedForBlock)
                .containsExactly(false, true, false, false, false);
    }

    @Test
    void reportUsesStrictLessThanThresholdAndMinimumTwoRatings() {
        UserProfileEntity exactlyTwoHalf = profile("threshold", 2, "2.50", UserProfileStatus.ACTIVE);
        UserProfileEntity belowThreshold = profile("below", 2, "2.49", UserProfileStatus.ACTIVE);
        UserProfileEntity singleLowVote = profile("single", 1, "1.00", UserProfileStatus.ACTIVE);

        when(userProfileRepository.findAll()).thenReturn(List.of(exactlyTwoHalf, belowThreshold, singleLowVote));
        when(matchRatingRepository.averageScore()).thenReturn(0.0);
        when(matchRatingRepository.count()).thenReturn(0L);

        List<UserRatingSummary> users = service().report().users();

        assertThat(users).extracting(UserRatingSummary::username)
                .containsExactly("single", "below", "threshold");
        assertThat(users).extracting(UserRatingSummary::recommendedForBlock)
                .containsExactly(false, true, false);
    }

    @Test
    void reportWithNoProfilesReturnsEmptyUserListAndZeroMetrics() {
        when(userProfileRepository.findAll()).thenReturn(List.of());
        when(matchRatingRepository.averageScore()).thenReturn(0.0);
        when(matchRatingRepository.count()).thenReturn(0L);

        AdminRatingsReportResponse report = service().report();

        assertThat(report.users()).isEmpty();
        assertThat(report.totalRatings()).isZero();
        assertThat(report.blockedUsersCount()).isZero();
        assertThat(report.platformAverageRating()).isZero();
    }

    @Test
    void blockChangesStatusAndReturnsUserStatus() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = profile("user1", 0, "0.00", UserProfileStatus.ACTIVE);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UserStatusResponse response = service().changeStatus(userId, UserProfileStatus.BLOCKED);

        assertThat(response.userId()).isEqualTo(profile.getId());
        assertThat(response.username()).isEqualTo("user1");
        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(profile.getStatus()).isEqualTo(UserProfileStatus.BLOCKED);
    }

    @Test
    void unblockChangesStatusAndReturnsUserStatus() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = profile("user1", 0, "0.00", UserProfileStatus.BLOCKED);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UserStatusResponse response = service().changeStatus(userId, UserProfileStatus.ACTIVE);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(profile.getStatus()).isEqualTo(UserProfileStatus.ACTIVE);
    }

    @Test
    void blockMissingUserFailsWithNotFound() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeStatus(userId, UserProfileStatus.BLOCKED))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_FOUND);
    }

    @Test
    void unblockMissingUserFailsWithNotFound() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeStatus(userId, UserProfileStatus.ACTIVE))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_FOUND);
    }

    private static UserProfileEntity profile(
            String username, int ratingsCount, String averageRating, UserProfileStatus status) {
        UserProfileEntity profile = new UserProfileEntity(UUID.randomUUID(), username, username + "@rutatur.local");
        profile.setRatingsCount(ratingsCount);
        profile.setAverageRating(new BigDecimal(averageRating));
        profile.setStatus(status);
        return profile;
    }
}
