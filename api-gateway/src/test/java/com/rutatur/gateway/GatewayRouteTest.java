package com.rutatur.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayRouteTest extends GatewayTestSupport {

    @Test
    void publicSearchIsForwardedWithoutTokenAndInternalHeaderIsStripped() {
        client.get()
                .uri("/api/pois/search?lat=-34.6037&lng=-58.3816&radius=500")
                .header("X-Internal-Token", "smuggled")
                .exchange()
                .expectStatus().isOk();

        assertThat(POI.requests()).hasSize(1);
        assertThat(POI.requests().getFirst().path()).startsWith("/api/pois/search");
        assertThat(POI.requests().getFirst().headers().getFirst("X-Internal-Token")).isNull();
    }

    @Test
    void generationIsForwardedToItineraryService() {
        client.post()
                .uri("/api/itinerary/generate")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        assertThat(ITINERARY.requests()).hasSize(1);
        assertThat(ITINERARY.requests().getFirst().path()).isEqualTo("/api/itinerary/generate");
    }

    @Test
    void savedItinerariesRequireUserRole() {
        client.get().uri("/api/itineraries").exchange().expectStatus().isUnauthorized();

        client.get()
                .uri("/api/itineraries")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isOk();

        client.get()
                .uri("/api/itineraries")
                .header("Authorization", "Bearer no-role-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminSurfaceRequiresAdminRole() {
        client.get()
                .uri("/api/admin/itineraries?userId=user1")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isForbidden();

        client.get()
                .uri("/api/admin/itineraries?userId=user1")
                .header("Authorization", "Bearer admin-token")
                .exchange()
                .expectStatus().isOk();

        assertThat(ITINERARY.requests()).hasSize(1);
    }

    @Test
    void routeMatchingIsForwardedToRouteMatchService() {
        client.get()
                .uri("/api/routes/shareable")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isOk();

        assertThat(ROUTES.requests()).hasSize(1);
        assertThat(ROUTES.requests().getFirst().path()).isEqualTo("/api/routes/shareable");
    }

    @Test
    void unknownBusinessPathReturns404WithoutForwarding() {
        client.get()
                .uri("/api/unknown")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(POI.requests()).isEmpty();
        assertThat(ITINERARY.requests()).isEmpty();
        assertThat(ROUTES.requests()).isEmpty();
    }

    @Test
    void internalPathsAreNeverForwarded() {
        client.get()
                .uri("/internal/pois/candidates?lat=-34.6&lng=-58.3&radius=500")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isNotFound();

        client.get()
                .uri("/internal/itineraries/00000000-0000-0000-0000-000000000001")
                .header("Authorization", "Bearer user-token")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(POI.requests()).isEmpty();
        assertThat(ITINERARY.requests()).isEmpty();
    }

    @Test
    void invalidTokenOnPublicPathIsRejected() {
        client.get()
                .uri("/api/pois/search?lat=-34.6037&lng=-58.3816&radius=500")
                .header("Authorization", "Bearer bad-token")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(POI.requests()).isEmpty();
    }
}
