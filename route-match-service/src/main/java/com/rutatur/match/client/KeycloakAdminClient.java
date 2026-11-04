package com.rutatur.match.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rutatur.common.dto.ContactDto;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.config.MatchProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Keycloak Admin lookup used only after mutual acceptance. The service account holds the
 * realm-management {@code view-users} role; no contact data is stored locally.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

    private final RestClient restClient;
    private final MatchProperties properties;

    public KeycloakAdminClient(RestClient.Builder builder, MatchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(trimSlash(properties.keycloakAdminUrl()))
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    public ContactDto lookupUser(String userId) {
        RuntimeException lastFailure = null;
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
            } catch (RestClientResponseException | ResourceAccessException ex) {
                lastFailure = ex;
                if (ex instanceof RestClientResponseException responseException
                        && responseException.getStatusCode().value() < 500) {
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

    private String fetchToken() {
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
        if (token == null || token.accessToken() == null) {
            throw new UpstreamServiceException(
                    ErrorCode.CONTACT_PROVIDER_UNAVAILABLE, "Contact provider is unavailable");
        }
        return token.accessToken();
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
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserRepresentation(String id, String username, String email) {
    }

    /** Visible for tests: the realm name used for both token and admin calls. */
    Map<String, String> realmCoordinates() {
        return Map.of("realm", properties.keycloakRealm(), "client", properties.keycloakClientId());
    }
}
