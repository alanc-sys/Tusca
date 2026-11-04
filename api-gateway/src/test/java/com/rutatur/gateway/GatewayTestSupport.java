package com.rutatur.gateway;

import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Shared stub upstreams and token fixtures for gateway integration tests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class GatewayTestSupport {

    static final StubServer POI = StubServer.start();
    static final StubServer ITINERARY = StubServer.start();
    static final StubServer ROUTES = StubServer.start();

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("POI_SERVICE_URI", () -> POI.baseUrl());
        registry.add("ITINERARY_SERVICE_URI", () -> ITINERARY.baseUrl());
        registry.add("ROUTE_MATCH_SERVICE_URI", () -> ROUTES.baseUrl());
        registry.add("RUTATUR_CORS_ORIGINS", () -> "http://localhost:3000");
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void stubTokens() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        when(jwtDecoder.decode("user-token")).thenReturn(Mono.just(jwt("user1", "ROLE_USER")));
        when(jwtDecoder.decode("admin-token"))
                .thenReturn(Mono.just(jwt("admin1", "ROLE_USER", "ROLE_ADMIN")));
        when(jwtDecoder.decode("no-role-token")).thenReturn(Mono.just(jwt("norole")));
        when(jwtDecoder.decode("bad-token")).thenReturn(Mono.error(new JwtException("invalid token")));
        POI.reset();
        ITINERARY.reset();
        ROUTES.reset();
    }

    static Jwt jwt(String subject, String... roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("value")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    /** Minimal HTTP server that records what the gateway forwarded. */
    static final class StubServer {

        record Recorded(String method, String path, Headers headers) {
        }

        private final HttpServer server;
        private final List<Recorded> requests = new CopyOnWriteArrayList<>();

        private StubServer(HttpServer server) {
            this.server = server;
        }

        static StubServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                StubServer stub = new StubServer(server);
                server.createContext("/", exchange -> {
                    stub.requests.add(new Recorded(
                            exchange.getRequestMethod(),
                            exchange.getRequestURI().toString(),
                            exchange.getRequestHeaders()));
                    byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                server.start();
                return stub;
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot start stub upstream", ex);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<Recorded> requests() {
            return List.copyOf(requests);
        }

        void reset() {
            requests.clear();
        }
    }
}
