package com.rutatur.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void unauthenticatedRequestToProtectedRouteReturns401ApiError() {
        webTestClient.get()
                .uri("/api/itineraries")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void userRoleCannotAccessAdminRoutesReturns403ApiError() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .get()
                .uri("/api/admin/routes")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void adminRoleCanAccessAdminRoutes() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get()
                .uri("/api/admin/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isNotEmpty();
    }

    @Test
    void externalRequestToInternalEndpointIsBlocked() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .get()
                .uri("/internal/pois/search")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void healthEndpointIsPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
