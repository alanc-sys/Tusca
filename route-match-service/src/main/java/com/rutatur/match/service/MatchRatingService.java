package com.rutatur.match.service;

import com.rutatur.common.dto.MatchRatingResponse;
import com.rutatur.common.dto.RateMatchRequest;
import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.entity.MatchRatingEntity;
import com.rutatur.match.entity.RouteMatchEntity;
import com.rutatur.match.entity.UserProfileEntity;
import com.rutatur.match.repository.MatchRatingRepository;
import com.rutatur.match.repository.RouteMatchRepository;
import com.rutatur.match.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Calificaciones mutuas posteriores a un match aceptado (RN-14, RN-15, RN-16).
 *
 * <p>El usuario calificado se deriva del match: es la contraparte del calificador. La reputación se
 * recalcula dentro de la misma transacción sumando todas las calificaciones recibidas, para no
 * arrastrar el redondeo de promedios anteriores.
 */
@Service
public class MatchRatingService {

    private final MatchRatingRepository matchRatingRepository;
    private final RouteMatchRepository routeMatchRepository;
    private final UserProfileRepository userProfileRepository;

    public MatchRatingService(
            MatchRatingRepository matchRatingRepository,
            RouteMatchRepository routeMatchRepository,
            UserProfileRepository userProfileRepository) {
        this.matchRatingRepository = matchRatingRepository;
        this.routeMatchRepository = routeMatchRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public MatchRatingResponse rate(UUID matchId, UUID raterUserId, RateMatchRequest request) {
        RouteMatchEntity match = routeMatchRepository.findById(matchId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.MATCH_NOT_ACCEPTED, "El match no existe o no está aceptado"));
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw new RutaturException(
                    ErrorCode.MATCH_NOT_ACCEPTED, "Solo se pueden calificar matches aceptados");
        }

        String raterId = raterUserId.toString();
        if (!match.involvesUser(raterId)) {
            throw new RutaturException(
                    ErrorCode.NOT_MATCH_PARTICIPANT, "El usuario no participa de este match");
        }

        UUID ratedUserId;
        try {
            ratedUserId = UUID.fromString(match.getCounterpartyRoute(raterId).getUserId());
        } catch (IllegalArgumentException ex) {
            throw new RutaturException(
                    ErrorCode.NOT_MATCH_PARTICIPANT, "Identificador de contraparte inválido");
        }

        if (matchRatingRepository.existsByMatchAndRater(matchId, raterUserId)) {
            throw new RutaturException(
                    ErrorCode.RATING_ALREADY_EXISTS, "Este match ya fue calificado por el usuario");
        }

        UserProfileEntity rater = userProfileRepository.findById(raterUserId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.USER_PROFILE_NOT_FOUND, "No existe el perfil del calificador"));
        UserProfileEntity rated = userProfileRepository.findById(ratedUserId)
                .orElseThrow(() -> new RutaturException(
                        ErrorCode.USER_PROFILE_NOT_FOUND, "No existe el perfil del calificado"));

        MatchRatingEntity saved = matchRatingRepository.save(
                new MatchRatingEntity(match, rater, rated, request.score(), request.comment()));
        recalculateReputation(ratedUserId, rated);

        return new MatchRatingResponse(
                saved.getId(),
                matchId,
                raterUserId,
                ratedUserId,
                saved.getScore(),
                saved.getComment(),
                saved.getCreatedAt());
    }

    /** RN-16: actualiza cantidad y promedio con escala 2 y {@link RoundingMode#HALF_UP}. */
    private void recalculateReputation(UUID ratedUserId, UserProfileEntity rated) {
        List<MatchRatingEntity> ratings = matchRatingRepository.findAllByRatedUserId(ratedUserId);
        BigDecimal total = ratings.stream()
                .map(rating -> BigDecimal.valueOf(rating.getScore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = ratings.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);

        rated.setRatingsCount(ratings.size());
        rated.setAverageRating(average);
    }
}
