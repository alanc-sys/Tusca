package com.rutatur.match.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rutatur.common.dto.ContactDto;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.config.MatchProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Keycloak Admin lookup used only after mutual acceptance. The service account holds the
 * realm-management {@code view-users} role; no contact data is stored locally. The
 * client-credentials token is cached until shortly before expiry.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);
    private static final long TOKEN_EXPIRY_SKEW_SECONDS = 30;
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 60;
    private static final String CIRCUIT_NAME = "keycloak";

    private final RestClient restClient;
    private final MatchProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Clock clock;

    private volatile String cachedToken;
    private volatile Instant cachedTokenValidUntil = Instant.EPOCH;

    @Autowired
    public KeycloakAdminClient(
            RestClient.Builder builder,
            MatchProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this(buildRestClient(builder, properties), properties, Clock.systemUTC(), circuitBreakerRegistry);
    }

    KeycloakAdminClient(
            RestClient restClient,
            MatchProperties properties,
            Clock clock,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
    }

    private static RestClient buildRestClient(RestClient.Builder builder, MatchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(trimSlash(properties.keycloakAdminUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public ContactDto lookupUser(String userId) {
        try {
            return circuitBreaker.executeSupplier(() -> lookupWithRetry(userId));
        } catch (CallNotPermittedException ex) {
            log.warn("Keycloak circuit is open; skipping the call");
            throw new UpstreamServiceException(
                    ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "Contact provider is unavailable", ex);
        }
    }

    private ContactDto lookupWithRetry(String userId) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String accessToken = fetchToken();
                UserRepresentation user = restClient.get()
                        .uri("/admin/realms/{realm}/users/{id}", properties.keycloakRealm(), userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .body(UserRepresentation.class);
                if (user == null || user.id() == null) {
                    throw new UpstreamServiceException(
                            ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "Contact provider is unavailable");
                }
                return new ContactDto(user.id(), user.username(), user.email());
            } catch (RestClientException ex) {
                lastFailure = ex;
                if (!isRetryable(ex)) {
                    break;
                }
            }
            if (attempt == 1) {
                sleep();
            }
        }
        log.warn("Keycloak lookup failed: {}",
                lastFailure == null ? "unknown" : lastFailure.getMessage());
        throw new UpstreamServiceException(
                ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "Contact provider is unavailable", lastFailure);
    }

    private synchronized String fetchToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedTokenValidUntil)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.keycloakClientId());
        form.add("client_secret", properties.keycloakClientSecret());

        TokenResponse token = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.keycloakRealm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new UpstreamServiceException(
                    ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "Contact provider is unavailable");
        }

        long ttlSeconds = token.expiresIn() == null || token.expiresIn() <= 0
                ? DEFAULT_TOKEN_TTL_SECONDS
                : token.expiresIn();
        cachedToken = token.accessToken();
        cachedTokenValidUntil = now.plusSeconds(Math.max(1, ttlSeconds - TOKEN_EXPIRY_SKEW_SECONDS));
        return cachedToken;
    }

    private static boolean isRetryable(RestClientException ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        return ex instanceof RestClientResponseException responseException
                && responseException.getStatusCode().value() >= 500;
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserRepresentation(String id, String username, String email) {
    }

    /** Visible for tests: the realm name used for both token and admin calls. */
    Map<String, String> realmCoordinates() {
        return Map.of("realm", properties.keycloakRealm(), "client", properties.keycloakClientId());
    }
}
