package com.rutatur.gateway.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modo demo: si no viene {@code Authorization} pero sí {@code X-Demo-User}, obtiene un JWT real de
 * Keycloak con el flujo {@code password}, lo cachea hasta 30 s antes de expirar y lo inyecta en el
 * request antes de la cadena de seguridad (RN-17 / modo demo de la spec).
 *
 * <p>Usa {@code @Order(-200)} para ejecutarse estrictamente antes de {@code WebFilterChainProxy}
 * de Spring Security (que corre en {@code -100}), evitando empates dependientes del orden de beans.
 */
@Component
@Profile("demo")
@Order(-200)
public class DemoUserAuthWebFilter implements WebFilter {

    public static final String DEMO_USER_HEADER = "X-Demo-User";

    private final WebClient tokenClient;
    private final ObjectMapper objectMapper;
    private final String tokenUri;
    private final String clientId;
    private final String password;
    private final long cacheSkewSeconds;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public DemoUserAuthWebFilter(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${rutatur.demo.token-uri}") String tokenUri,
            @Value("${rutatur.demo.client-id}") String clientId,
            @Value("${rutatur.demo.password}") String password,
            @Value("${rutatur.demo.cache-skew-seconds:30}") long cacheSkewSeconds) {
        this(webClientBuilder.build(), objectMapper, tokenUri, clientId, password, cacheSkewSeconds);
    }

    /** Constructor paquete-privado para tests unitarios con un {@link WebClient} controlado. */
    DemoUserAuthWebFilter(
            WebClient tokenClient,
            ObjectMapper objectMapper,
            String tokenUri,
            String clientId,
            String password,
            long cacheSkewSeconds) {
        this.tokenClient = tokenClient;
        this.objectMapper = objectMapper;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.password = password;
        this.cacheSkewSeconds = cacheSkewSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return chain.filter(exchange);
        }

        String demoUser = request.getHeaders().getFirst(DEMO_USER_HEADER);
        if (demoUser == null || demoUser.isBlank()) {
            return chain.filter(exchange);
        }

        CachedToken cached = tokenCache.get(demoUser.trim());
        if (isUsable(cached)) {
            return chain.filter(withBearerToken(exchange, cached.accessToken()));
        }

        String username = demoUser.trim();
        return requestToken(username)
                .onErrorResume(ex -> writeUnauthorized(exchange, username).then(Mono.empty()))
                .flatMap(token -> {
                    long ttl = Math.max(0L, token.expiresIn() - cacheSkewSeconds);
                    tokenCache.put(username, new CachedToken(token.accessToken(), Instant.now().plusSeconds(ttl)));
                    return chain.filter(withBearerToken(exchange, token.accessToken()));
                });
    }

    private Mono<Token> requestToken(String username) {
        return tokenClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", clientId)
                        .with("username", username)
                        .with("password", password))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .filter(response -> response.accessToken() != null && !response.accessToken().isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException("Keycloak no devolvió access_token")))
                .map(response -> new Token(
                        response.accessToken(),
                        response.expiresIn() <= 0 ? cacheSkewSeconds + 1 : response.expiresIn()));
    }

    private static boolean isUsable(CachedToken cached) {
        return cached != null && cached.expiresAt().isAfter(Instant.now());
    }

    private static ServerWebExchange withBearerToken(ServerWebExchange exchange, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(exchange.getRequest().getHeaders());
        headers.setBearerAuth(jwt);

        ServerHttpRequest mutated = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String demoUser) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError error = ApiError.of(
                ErrorCode.UNAUTHORIZED,
                "No se pudo obtener un token demo para " + demoUser,
                exchange.getRequest().getPath().value());
        try {
            byte[] payload = objectMapper.writeValueAsBytes(error);
            DataBuffer buffer = response.bufferFactory().wrap(payload);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }

    private record Token(String accessToken, long expiresIn) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
