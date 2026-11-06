package com.rutatur.match.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.Category;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimilarityScorerTest {

    private static final Instant SHARED_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final GeoPointDto START = new GeoPointDto(-34.6037, -58.3816);

    private final SimilarityScorer scorer = new SimilarityScorer();

    @Test
    void identicalRoutesScoreExactlyOne() {
        RouteProfile a = profile("owner-a", START, List.of("p1", "p2"), Set.of(Category.PARK), 120);
        RouteProfile b = profile("owner-b", START, List.of("p1", "p2"), Set.of(Category.PARK), 120);

        assertThat(scorer.score(a, b)).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    void jaccardContributesFortyPercent() {
        RouteProfile a = profile("owner-a", START, List.of("p1"), Set.of(Category.PARK), 120);
        RouteProfile b = profile("owner-b", START, List.of("p2"), Set.of(Category.PARK), 120);

        // 0.25 + 0.20 + 0.15 = 0.60
        assertThat(scorer.score(a, b)).isEqualByComparingTo(new BigDecimal("0.6000"));
    }

    @Test
    void startProximitySaturatesAtFiveKilometers() {
        RouteProfile near = profile("owner-a", START, List.of("p1"), Set.of(Category.PARK), 120);
        RouteProfile far = profile(
                "owner-b", new GeoPointDto(-34.65, -58.45), List.of("p1"), Set.of(Category.PARK), 120);

        // 0.40 + 0.20 + 0.15 = 0.75 when proximity hits zero
        assertThat(scorer.score(near, far)).isEqualByComparingTo(new BigDecimal("0.7500"));
    }

    @Test
    void timeSimilarityUsesRelativeDifference() {
        RouteProfile shortRoute = profile("owner-a", START, List.of(), Set.of(), 30);
        RouteProfile longRoute = profile("owner-b", START, List.of(), Set.of(), 480);

        // 0.25 (same start) + 0.15 * 0.0625 = 0.259375 -> 0.2594
        assertThat(scorer.score(shortRoute, longRoute)).isEqualByComparingTo(new BigDecimal("0.2594"));
    }

    @Test
    void emptyUnionsScoreZeroInsteadOfNaN() {
        RouteProfile a = profile("owner-a", START, List.of(), Set.of(), 120);
        RouteProfile b = profile("owner-b", START, List.of(), Set.of(), 120);

        // Empty unions contribute 0; identical start and time contribute 0.25 + 0.15.
        assertThat(scorer.score(a, b)).isEqualByComparingTo(new BigDecimal("0.4000"));
        assertThat(Double.isNaN(scorer.score(a, b).doubleValue())).isFalse();
    }

    @Test
    void roundsHalfUpToFourDecimals() {
        RouteProfile a = profile("owner-a", START, List.of("p1"), Set.of(), 30);
        RouteProfile b = profile("owner-b", START, List.of("p1"), Set.of(), 480);

        // 0.40 + 0.25 + 0 + 0.009375 = 0.659375 -> 0.6594
        assertThat(scorer.score(a, b)).isEqualByComparingTo(new BigDecimal("0.6594"));
        assertThat(scorer.score(a, b).scale()).isEqualTo(4);
    }

    @Test
    void matchedPoiIdsAreSortedAscending() {
        RouteProfile a = profile("owner-a", START, List.of("p2", "p1", "p3"), Set.of(), 120);
        RouteProfile b = profile("owner-b", START, List.of("p3", "p1", "p4"), Set.of(), 120);

        assertThat(scorer.matchedPoiIds(a, b)).containsExactly("p1", "p3");
    }

    private static RouteProfile profile(
            String owner, GeoPointDto start, List<String> poiIds, Set<Category> categories, int time) {
        return new RouteProfile(UUID.randomUUID(), owner, start, poiIds, categories, time, SHARED_AT);
    }
}
