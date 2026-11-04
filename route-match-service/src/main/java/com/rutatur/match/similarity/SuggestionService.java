package com.rutatur.match.similarity;

import com.rutatur.common.dto.MatchSuggestionDto;
import com.rutatur.match.config.MatchProperties;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds suggestions from other users' active shares. Only scores at or above the configured
 * threshold appear; ordering is score desc then target route id asc, so ties are deterministic.
 */
@Service
public class SuggestionService {

    private final SharedRouteRepository sharedRouteRepository;
    private final SimilarityScorer scorer;
    private final MatchProperties properties;
    private final Clock clock;

    public SuggestionService(
            SharedRouteRepository sharedRouteRepository,
            SimilarityScorer scorer,
            MatchProperties properties,
            Clock clock) {
        this.sharedRouteRepository = sharedRouteRepository;
        this.scorer = scorer;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MatchSuggestionDto> suggestions(String callerUserId) {
        Instant now = clock.instant();
        List<RouteProfile> callerShares = sharedRouteRepository
                .findActiveByOwner(callerUserId, now)
                .stream()
                .map(RouteProfile::from)
                .toList();
        if (callerShares.isEmpty()) {
            return List.of();
        }

        List<MatchSuggestionDto> suggestions = new ArrayList<>();
        for (SharedRouteEntity targetEntity : sharedRouteRepository.findActivePool(callerUserId, now)) {
            RouteProfile target = RouteProfile.from(targetEntity);
            RouteProfile best = null;
            BigDecimal bestScore = null;
            for (RouteProfile candidate : callerShares) {
                BigDecimal score = scorer.score(candidate, target);
                if (bestScore == null || score.compareTo(bestScore) > 0) {
                    best = candidate;
                    bestScore = score;
                }
            }
            if (best == null || bestScore == null
                    || bestScore.compareTo(properties.matchThreshold()) < 0) {
                continue;
            }
            List<String> matched = scorer.matchedPoiIds(best, target);
            suggestions.add(new MatchSuggestionDto(
                    target.id(),
                    best.id(),
                    bestScore.doubleValue(),
                    matched.size(),
                    matched,
                    target.createdAt()));
        }

        return suggestions.stream()
                .sorted(Comparator.comparingDouble(MatchSuggestionDto::matchScore)
                        .reversed()
                        .thenComparing(MatchSuggestionDto::targetRouteId))
                .toList();
    }

    /** Highest scoring caller share against one target, used by the request flow. */
    public ScoredPair bestPair(String callerUserId, RouteProfile target, Instant now) {
        List<SharedRouteEntity> callerShares =
                sharedRouteRepository.findActiveByOwner(callerUserId, now);
        ScoredPair best = null;
        for (SharedRouteEntity candidate : callerShares) {
            RouteProfile profile = RouteProfile.from(candidate);
            BigDecimal score = scorer.score(profile, target);
            if (best == null || score.compareTo(best.score()) > 0) {
                best = new ScoredPair(profile, score, scorer.matchedPoiIds(profile, target));
            }
        }
        return best;
    }

    /** Best caller share and its similarity to a target share. */
    public record ScoredPair(RouteProfile source, BigDecimal score, List<String> matchedPoiIds) {
    }
}
