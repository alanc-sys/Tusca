package com.rutatur.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DocsAggregationTest extends GatewayTestSupport {

    @Test
    void swaggerConfigAdvertisesOnlyProxiedDocs() {
        String body = client.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body)
                .contains("/v3/api-docs/pois")
                .contains("/v3/api-docs/itineraries")
                .contains("/v3/api-docs/routes");
        assertThat(body).doesNotContain("/api/admin/swagger");
    }

    @Test
    void docsRouteIsRewrittenBeforeForwarding() {
        client.get()
                .uri("/v3/api-docs/pois")
                .exchange()
                .expectStatus().isOk();

        assertThat(POI.requests()).hasSize(1);
        assertThat(POI.requests().getFirst().path()).isEqualTo("/v3/api-docs");
        assertThat(POI.requests().getFirst().headers().getFirst("X-Internal-Token")).isNull();
    }

    @Test
    void swaggerUiIsPublic() {
        client.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }
}
