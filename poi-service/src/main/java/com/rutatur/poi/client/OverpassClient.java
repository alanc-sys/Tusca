package com.rutatur.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.config.PoiProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Overpass integration: POST a QL body, parse OSM JSON, retry once on transient failures.
 * A 4xx is never retried; every failure surfaces as {@code 503 POI_PROVIDER_UNAVAILABLE}.
 * The configured circuit breaker stops calling a failing provider until it recovers.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);
    private static final String CIRCUIT_NAME = "overpass";

    private final RestClient restClient;
    private final OverpassQueryBuilder queryBuilder;
    private final CircuitBreaker circuitBreaker;
    private final Duration retryBackoff;

    public OverpassClient(
            RestClient.Builder builder,
            PoiProperties properties,
            OverpassQueryBuilder queryBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(properties.overpassUrl())
                .requestFactory(requestFactory)
                .build();
        this.queryBuilder = queryBuilder;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
        this.retryBackoff = Duration.ofMillis(500);
    }

    public JsonNode fetch(double lat, double lng, int radius, List<Category> categories) {
        String query = queryBuilder.build(lat, lng, radius, categories);
        try {
            return circuitBreaker.executeSupplier(() -> fetchWithRetry(query));
        } catch (CallNotPermittedException ex) {
            log.warn("Overpass circuit is open; skipping the call");
            throw new UpstreamServiceException(
                    ErrorCode.POI_PROVIDER_UNAVAILABLE, "POI provider is unavailable", ex);
        }
    }

    private JsonNode fetchWithRetry(String query) {
        RestClientException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("data", query);
                JsonNode body = restClient.post()
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null || body.isNull()) {
                    throw new UpstreamServiceException(
                            ErrorCode.POI_PROVIDER_UNAVAILABLE, "POI provider returned no body");
                }
                return body;
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

        log.warn("Overpass call failed: {}", lastFailure == null ? "unknown" : lastFailure.getMessage());
        throw new UpstreamServiceException(
                ErrorCode.POI_PROVIDER_UNAVAILABLE, "POI provider is unavailable", lastFailure);
    }

    private static boolean isRetryable(RestClientException ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
