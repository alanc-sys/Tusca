package com.rutatur.itinerary.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.ItineraryRequestDto;
import com.rutatur.common.dto.ItineraryResponseDto;
import com.rutatur.common.dto.ItineraryStopDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.Category;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.BusinessException;
import com.rutatur.itinerary.client.RoutingMatrix;
import com.rutatur.itinerary.config.ItineraryProperties;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItineraryOptimizerTest {

    private static final GeoPointDto START = new GeoPointDto(-34.6037, -58.3816);
    private static final ItineraryProperties PROPERTIES =
            new ItineraryProperties(null, null, LocalTime.of(9, 0), 50, null, null);

    private final CandidateScorer scorer = new CandidateScorer();
    private final ItineraryOptimizer optimizer = new ItineraryOptimizer(scorer, PROPERTIES);

    @Test
    void keepsRouteWithinAvailableTime() {
        ItineraryRequestDto request = request(60, List.of(Category.MUSEUM), 2);
        List<PoiDto> candidates = List.of(
                poi("osm:node/1", Category.MUSEUM, 20, 4.5),
                poi("osm:node/2", Category.MUSEUM, 20, 4.0));
        RoutingMatrix matrix = matrix(
                new int[][] {
                    {0, 10, 15},
                    {10, 0, 10},
                    {15, 10, 0}
                },
                new int[][] {
                    {0, 500, 800},
                    {500, 0, 400},
                    {800, 400, 0}
                });

        ItineraryResponseDto response =
                optimizer.buildRoute(UUID.randomUUID(), request, candidates, matrix);

        assertThat(response.totalTime()).isLessThanOrEqualTo(request.availableTime());
        assertThat(response.totalVisitTime() + response.totalTravelTime())
                .isEqualTo(response.totalTime());
        assertThat(response.endTime()).isEqualTo(LocalTime.of(9, 0).plusMinutes(response.totalTime()));
        assertThat(response.route()).hasSize(2);
        assertThat(response.route())
                .extracting(stop -> stop.poi().id())
                .doesNotHaveDuplicates();
    }

    @Test
    void deduplicatesCandidatesByIdBeforeRouting() {
        ItineraryRequestDto request = request(120, List.of(Category.MUSEUM), 6);
        PoiDto first = poi("osm:node/1", Category.MUSEUM, 20, 4.5);
        PoiDto duplicate = poi("osm:node/1", Category.MUSEUM, 20, 4.5);

        List<PoiDto> ranked = optimizer.rank(request, List.of(first, duplicate));

        assertThat(ranked).hasSize(1);
    }

    @Test
    void skipsCandidateThatDoesNotFitAndContinues() {
        ItineraryRequestDto request = request(60, List.of(Category.MUSEUM), 6);
        List<PoiDto> candidates = List.of(
                poi("osm:node/1", Category.MUSEUM, 120, 5.0),
                poi("osm:node/2", Category.MUSEUM, 20, 4.0));
        RoutingMatrix matrix = matrix(
                new int[][] {
                    {0, 5, 5},
                    {5, 0, 5},
                    {5, 5, 0}
                },
                new int[][] {
                    {0, 100, 100},
                    {100, 0, 100},
                    {100, 100, 0}
                });

        ItineraryResponseDto response =
                optimizer.buildRoute(UUID.randomUUID(), request, candidates, matrix);

        assertThat(response.route()).hasSize(1);
        assertThat(response.route().getFirst().poi().id()).isEqualTo("osm:node/2");
    }

    @Test
    void rejectsWhenNoCandidateFits() {
        ItineraryRequestDto request = request(30, List.of(Category.MUSEUM), 6);
        List<PoiDto> candidates = List.of(poi("osm:node/1", Category.MUSEUM, 120, 5.0));
        RoutingMatrix matrix = matrix(
                new int[][] {{0, 5}, {5, 0}}, new int[][] {{0, 100}, {100, 0}});

        assertThatThrownBy(() -> optimizer.buildRoute(UUID.randomUUID(), request, candidates, matrix))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ITINERARY_NOT_FEASIBLE);
    }

    @Test
    void skipsUnreachableLegs() {
        ItineraryRequestDto request = request(120, List.of(Category.MUSEUM), 6);
        List<PoiDto> candidates = List.of(
                poi("osm:node/1", Category.MUSEUM, 20, 5.0),
                poi("osm:node/2", Category.MUSEUM, 20, 4.0));
        RoutingMatrix matrix = matrix(
                new int[][] {
                    {0, RoutingMatrix.UNREACHABLE, 10},
                    {0, 0, 0},
                    {10, 0, 0}
                },
                new int[][] {
                    {0, 100, 900},
                    {0, 0, 0},
                    {900, 0, 0}
                });

        ItineraryResponseDto response =
                optimizer.buildRoute(UUID.randomUUID(), request, candidates, matrix);

        assertThat(response.route()).hasSize(1);
        assertThat(response.route().getFirst().poi().id()).isEqualTo("osm:node/2");
    }

    @Test
    void respectsMaxPois() {
        ItineraryRequestDto request = request(240, List.of(Category.MUSEUM), 2);
        List<PoiDto> candidates = List.of(
                poi("osm:node/1", Category.MUSEUM, 10, 5.0),
                poi("osm:node/2", Category.MUSEUM, 10, 4.0),
                poi("osm:node/3", Category.MUSEUM, 10, 3.0));
        RoutingMatrix matrix = matrix(
                new int[][] {
                    {0, 5, 5, 5},
                    {5, 0, 5, 5},
                    {5, 5, 0, 5},
                    {5, 5, 5, 0}
                },
                new int[][] {
                    {0, 100, 100, 100},
                    {100, 0, 100, 100},
                    {100, 100, 0, 100},
                    {100, 100, 100, 0}
                });

        ItineraryResponseDto response =
                optimizer.buildRoute(UUID.randomUUID(), request, candidates, matrix);

        assertThat(response.route()).hasSize(2);
    }

    @Test
    void ranksDeterministicallyWithPreferencePriority() {
        ItineraryRequestDto request =
                request(120, List.of(Category.PARK, Category.MUSEUM), 6);
        List<PoiDto> candidates = List.of(
                poi("osm:node/1", Category.RESTAURANT, 20, 5.0),
                poi("osm:node/2", Category.MUSEUM, 20, 1.0),
                poi("osm:node/3", Category.PARK, 20, 1.0));

        List<PoiDto> firstPass = optimizer.rank(request, candidates);
        List<PoiDto> secondPass = optimizer.rank(request, candidates);

        assertThat(firstPass).extracting(PoiDto::id)
                .containsExactly("osm:node/3", "osm:node/2", "osm:node/1");
        assertThat(secondPass).isEqualTo(firstPass);
    }

    @Test
    void filtersCandidatesOutsideRadius() {
        ItineraryRequestDto request = request(120, List.of(Category.MUSEUM), 6);
        PoiDto near = poi("osm:node/1", Category.MUSEUM, 20, 4.0);
        PoiDto far = new PoiDto(
                "osm:node/2",
                "Far",
                Category.MUSEUM,
                new GeoPointDto(-34.70, -58.50),
                null,
                20,
                4.0,
                List.of());

        List<PoiDto> ranked = optimizer.rank(request, List.of(near, far));

        assertThat(ranked).extracting(PoiDto::id).containsExactly("osm:node/1");
    }

    @Test
    void scoreWeightsPreferencesAboveRating() {
        double preferred = scorer.score(
                poi("osm:node/1", Category.PARK, 20, 0.0), List.of(Category.PARK));
        double unlistedTopRated = scorer.score(
                poi("osm:node/2", Category.RESTAURANT, 20, 5.0), List.of(Category.PARK));

        assertThat(preferred).isGreaterThan(unlistedTopRated);
    }

    private static ItineraryRequestDto request(int availableTime, List<Category> preferences, int maxPois) {
        return new ItineraryRequestDto(
                START, availableTime, preferences, 3000, TransportMode.WALKING, maxPois);
    }

    private static PoiDto poi(String id, Category category, int visitTime, double rating) {
        return new PoiDto(
                id, id, category, new GeoPointDto(-34.6038, -58.3817), null, visitTime, rating, List.of());
    }

    private static RoutingMatrix matrix(int[][] durations, int[][] distances) {
        return new RoutingMatrix(durations, distances);
    }
}
