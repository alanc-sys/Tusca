package com.rutatur.match.service;

import com.rutatur.common.dto.MatchRatingResponse;
import com.rutatur.common.dto.RateMatchRequest;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.entity.MatchRatingEntity;
import com.rutatur.match.entity.RouteMatchEntity;
import com.rutatur.match.entity.SharedRouteEntity;
import com.rutatur.match.entity.UserProfileEntity;
import com.rutatur.match.repository.MatchRatingRepository;
import com.rutatur.match.repository.RouteMatchRepository;
import com.rutatur.match.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchRatingServiceTest {

    private static final UUID MATCH_ID = UUID.randomUUID();
    private static final UUID RATER_ID = UUID.randomUUID();
    private static final UUID RATED_ID = UUID.randomUUID();
    private static final UUID SAVED_ID = UUID.randomUUID();

    @Mock
    private MatchRatingRepository matchRatingRepository;

    @Mock
    private RouteMatchRepository routeMatchRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    private final List<MatchRatingEntity> storedRatings = new ArrayList<>();
    private MatchRatingService service;

    @BeforeEach
    void setUp() {
        service = new MatchRatingService(matchRatingRepository, routeMatchRepository, userProfileRepository);
        storedRatings.clear();
    }

    @Test
    void ratesAcceptedMatchAndRecalculatesAverageWithMultipleVotes() {
        UserProfileEntity rater = rater();
        UserProfileEntity rated = rated();
        stubAcceptedMatch();
        stubProfiles(rater, rated);
        when(matchRatingRepository.existsByMatchAndRater(MATCH_ID, RATER_ID)).thenReturn(false);
        storedRatings.add(new MatchRatingEntity(null, rater, rated, 4, null));
        storedRatings.add(new MatchRatingEntity(null, rater, rated, 5, null));
        stubSuccessfulSave();

        MatchRatingResponse response = service.rate(
                MATCH_ID, RATER_ID, new RateMatchRequest(3, "buena ruta"));

        assertThat(response.id()).isEqualTo(SAVED_ID);
        assertThat(response.matchId()).isEqualTo(MATCH_ID);
        assertThat(response.raterUserId()).isEqualTo(RATER_ID);
        assertThat(response.ratedUserId()).isEqualTo(RATED_ID);
        assertThat(response.score()).isEqualTo(3);
        assertThat(response.comment()).isEqualTo("buena ruta");
        assertThat(rated.getRatingsCount()).isEqualTo(3);
        assertThat(rated.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.00"));
        verify(matchRatingRepository).save(any());
    }

    @Test
    void appliesHalfUpRoundingToTwoDecimals() {
        UserProfileEntity rater = rater();
        UserProfileEntity rated = rated();
        stubAcceptedMatch();
        stubProfiles(rater, rated);
        when(matchRatingRepository.existsByMatchAndRater(MATCH_ID, RATER_ID)).thenReturn(false);
        storedRatings.add(new MatchRatingEntity(null, rater, rated, 1, null));
        storedRatings.add(new MatchRatingEntity(null, rater, rated, 2, null));
        stubSuccessfulSave();

        service.rate(MATCH_ID, RATER_ID, new RateMatchRequest(2, null));

        assertThat(rated.getRatingsCount()).isEqualTo(3);
        assertThat(rated.getAverageRating()).isEqualByComparingTo(new BigDecimal("1.67"));
    }

    @Test
    void nonAcceptedMatchFailsWithConflict() {
        RouteMatchEntity match = acceptedMatch();
        match.setStatus(MatchStatus.PENDING);
        when(routeMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.rate(MATCH_ID, RATER_ID, new RateMatchRequest(5, null)))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MATCH_NOT_ACCEPTED);
        verify(matchRatingRepository, never()).save(any());
    }

    @Test
    void missingMatchFailsWithConflict() {
        when(routeMatchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(MATCH_ID, RATER_ID, new RateMatchRequest(5, null)))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MATCH_NOT_ACCEPTED);
    }

    @Test
    void userOutsideMatchIsForbidden() {
        stubAcceptedMatch();
        UUID outsider = UUID.randomUUID();

        assertThatThrownBy(() -> service.rate(MATCH_ID, outsider, new RateMatchRequest(5, null)))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_MATCH_PARTICIPANT);
        verify(matchRatingRepository, never()).save(any());
    }

    @Test
    void duplicateRatingFailsWithConflict() {
        stubAcceptedMatch();
        when(matchRatingRepository.existsByMatchAndRater(MATCH_ID, RATER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.rate(MATCH_ID, RATER_ID, new RateMatchRequest(5, null)))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RATING_ALREADY_EXISTS);
        verify(matchRatingRepository, never()).save(any());
    }

    @Test
    void missingProfileFailsWithNotFound() {
        stubAcceptedMatch();
        when(matchRatingRepository.existsByMatchAndRater(MATCH_ID, RATER_ID)).thenReturn(false);
        when(userProfileRepository.findById(RATER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(MATCH_ID, RATER_ID, new RateMatchRequest(5, null)))
                .isInstanceOf(RutaturException.class)
                .extracting(ex -> ((RutaturException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_FOUND);
        verify(matchRatingRepository, never()).save(any());
    }

    private void stubAcceptedMatch() {
        when(routeMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(acceptedMatch()));
    }

    private void stubProfiles(UserProfileEntity rater, UserProfileEntity rated) {
        when(userProfileRepository.findById(RATER_ID)).thenReturn(Optional.of(rater));
        when(userProfileRepository.findById(RATED_ID)).thenReturn(Optional.of(rated));
    }

    private void stubSuccessfulSave() {
        when(matchRatingRepository.save(any())).thenAnswer(invocation -> {
            MatchRatingEntity saved = invocation.getArgument(0);
            saved.setId(SAVED_ID);
            storedRatings.add(saved);
            return saved;
        });
        when(matchRatingRepository.findAllByRatedUserId(RATED_ID))
                .thenAnswer(invocation -> List.copyOf(storedRatings));
    }

    private static RouteMatchEntity acceptedMatch() {
        RouteMatchEntity match = new RouteMatchEntity();
        match.setId(MATCH_ID);
        match.setStatus(MatchStatus.ACCEPTED);
        SharedRouteEntity raterRoute = new SharedRouteEntity();
        raterRoute.setUserId(RATER_ID.toString());
        SharedRouteEntity ratedRoute = new SharedRouteEntity();
        ratedRoute.setUserId(RATED_ID.toString());
        match.setSharedRouteA(raterRoute);
        match.setSharedRouteB(ratedRoute);
        return match;
    }

    private static UserProfileEntity rater() {
        return new UserProfileEntity(RATER_ID, "rater", "rater@rutatur.local");
    }

    private static UserProfileEntity rated() {
        return new UserProfileEntity(RATED_ID, "rated", "rated@rutatur.local");
    }
}
