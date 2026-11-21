package com.rutatur.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DemoUserAuthWebFilterTest {

    private static final String TOKEN_URI = "http://keycloak:8080/realms/rutatur/protocol/openid-connect/token";
    private static final String DEFAULT_BODY = "{\"access_token\":\"jwt-user1\",\"expires_in\":300}";

    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final Queue<String> responses = new ConcurrentLinkedQueue<>();
    private DemoUserAuthWebFilter filter;

    @BeforeEach
    void setUp() {
        responses.clear();
        tokenCalls.set(0);

        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    tokenCalls.incrementAndGet();
                    String body = responses.isEmpty() ? DEFAULT_BODY : responses.poll();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new DemoUserAuthWebFilter(webClient, mapper, TOKEN_URI, "rutatur-api", "password", 30);
    }

    @Test
    void injectsBearerTokenFromKeycloakWhenDemoHeaderPresent() {
        AtomicReference<ServerWebExchange> passed = new AtomicReference<>();

        filter.filter(exchangeWithDemoUser("user1"), capture(passed)).block();

        assertThat(passed.get()).isNotNull();
        assertThat(passed.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer jwt-user1");
        assertThat(tokenCalls.get()).isEqualTo(1);
    }

    @Test
    void reusesCachedTokenForSameUser() {
        AtomicReference<ServerWebExchange> first = new AtomicReference<>();
        AtomicReference<ServerWebExchange> second = new AtomicReference<>();

        filter.filter(exchangeWithDemoUser("user2"), capture(first)).block();
        filter.filter(exchangeWithDemoUser("user2"), capture(second)).block();

        assertThat(first.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer jwt-user1");
        assertThat(second.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer jwt-user1");
        assertThat(tokenCalls.get()).isEqualTo(1);
    }

    @Test
    void doesNotCacheTokenExpiringWithinSkewWindow() {
        responses.add("{\"access_token\":\"short-lived\",\"expires_in\":10}");
        responses.add("{\"access_token\":\"fresh\",\"expires_in\":300}");
        AtomicReference<ServerWebExchange> first = new AtomicReference<>();
        AtomicReference<ServerWebExchange> second = new AtomicReference<>();

        filter.filter(exchangeWithDemoUser("user1"), capture(first)).block();
        filter.filter(exchangeWithDemoUser("user1"), capture(second)).block();

        assertThat(first.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer short-lived");
        assertThat(second.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer fresh");
        assertThat(tokenCalls.get()).isEqualTo(2);
    }

    @Test
    void passesThroughWhenAuthorizationHeaderAlreadyPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/itineraries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer real-token")
                .header("X-Demo-User", "user1")
                .build();
        AtomicReference<ServerWebExchange> passed = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), capture(passed)).block();

        assertThat(passed.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer real-token");
        assertThat(tokenCalls.get()).isZero();
    }

    @Test
    void passesThroughWhenDemoHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/itineraries").build();
        AtomicReference<ServerWebExchange> passed = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), capture(passed)).block();

        assertThat(passed.get().getRequest().getHeaders()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
        assertThat(tokenCalls.get()).isZero();
    }

    private static MockServerWebExchange exchangeWithDemoUser(String user) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/itineraries").header("X-Demo-User", user).build());
    }

    private static WebFilterChain capture(AtomicReference<ServerWebExchange> ref) {
        return exchange -> {
            ref.set(exchange);
            return Mono.empty();
        };
    }
}
