package com.rutatur.match.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.rutatur.common.dto.MatchSuggestionDto;
import com.rutatur.common.enums.Category;
import com.rutatur.match.config.MatchProperties;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MatchProperties PROPERTIES =
            new MatchProperties(null, null, null, null, null, null, null, null, new BigDecimal("0.35"));

    private final SharedRouteRepository repository = mock(SharedRouteRepository.class);
    private final SimilarityScorer scorer = mock(SimilarityScorer.class);
    private final SuggestionService service =
            new SuggestionService(repository, scorer, PROPERTIES, CLOCK);

    private SharedRouteEntity callerShare;
    private SharedRouteEntity high;
    private SharedRouteEntity medium;
    private SharedRouteEntity belowThreshold;

    @BeforeEach
    void setUp() {
        callerShare = share("caller", "p1", "p2");
        high = share("b1", "p1", "p2", "p3");
        medium = share("b2", "p2", "p3");
        belowThreshold = share("b3", "p4");

        when(repository.findActiveByOwner(eq("caller"), any()))
                .thenReturn(List.of(callerShare));
        when(repository.findActivePool(eq("caller"), any()))
                .thenReturn(List.of(high, medium, belowThreshold));
        when(scorer.score(any(), any())).thenAnswer(invocation -> {
            RouteProfile target = invocation.getArgument(1);
            return scoreFor(target.id());
        });
        when(scorer.matchedPoiIds(any(), any())).thenReturn(List.of("p1", "p2"));
    }

    @Test
    void keepsOnlyScoresAtOrAboveThresholdInDescendingOrder() {
        List<MatchSuggestionDto> suggestions = service.suggestions("caller");

        assertThat(suggestions).extracting(MatchSuggestionDto::targetRouteId)
                .containsExactly(high.getId(), medium.getId());
        assertThat(suggestions).extracting(MatchSuggestionDto::matchScore)
                .containsExactly(0.90, 0.42);
        assertThat(suggestions).extracting(MatchSuggestionDto::sourceRouteId)
                .containsOnly(callerShare.getId());
    }

    @Test
    void excludesCallerWithNoActiveShare() {
        when(repository.findActiveByOwner(eq("nobody"), any())).thenReturn(List.of());

        assertThat(service.suggestions("nobody")).isEmpty();
    }

    @Test
    void picksTheHighestScoringCallerShare() {
        SharedRouteEntity secondShare = share("caller", "p9");
        when(repository.findActiveByOwner(eq("caller"), any()))
                .thenReturn(List.of(callerShare, secondShare));
        reset(scorer);
        when(scorer.score(any(), any())).thenAnswer(invocation -> {
            RouteProfile source = invocation.getArgument(0);
            return source.id().equals(secondShare.getId())
                    ? new BigDecimal("0.9500")
                    : new BigDecimal("0.4000");
        });
        when(scorer.matchedPoiIds(any(), any())).thenReturn(List.of("p1"));

        List<MatchSuggestionDto> suggestions = service.suggestions("caller");

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst().sourceRouteId()).isEqualTo(secondShare.getId());
    }

    @Test
    void breaksScoreTiesByTargetRouteIdAscending() {
        reset(scorer);
        when(scorer.score(any(), any())).thenReturn(new BigDecimal("0.5000"));
        when(scorer.matchedPoiIds(any(), any())).thenReturn(List.of());

        List<MatchSuggestionDto> suggestions = service.suggestions("caller");

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions).extracting(MatchSuggestionDto::targetRouteId).isSorted();
    }

    private BigDecimal scoreFor(UUID targetId) {
        if (targetId.equals(high.getId())) {
            return new BigDecimal("0.9000");
        }
        if (targetId.equals(medium.getId())) {
            return new BigDecimal("0.4200");
        }
        return new BigDecimal("0.3490");
    }

    private static SharedRouteEntity share(String owner, String... poiIds) {
        return new SharedRouteEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                owner,
                BigDecimal.valueOf(-34.6037),
                BigDecimal.valueOf(-58.3816),
                120,
                "WALKING",
                90,
                List.of(poiIds),
                List.of(Category.PARK.name()),
                true,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600));
    }
}
