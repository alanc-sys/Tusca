package com.rutatur.match.client;

import com.rutatur.common.dto.InternalItineraryDto;
import com.rutatur.common.dto.InternalItinerarySummary;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.config.MatchProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Reads itinerary snapshots through the internal token endpoint; never touches the itinerary schema. */
@Component
public class ItineraryInternalClient {

    private static final Logger log = LoggerFactory.getLogger(ItineraryInternalClient.class);
    private static final String CIRCUIT_NAME = "itineraryService";
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

    private final RestClient restClient;
    private final String internalToken;
    private final CircuitBreaker circuitBreaker;

    public ItineraryInternalClient(
            RestClient.Builder builder,
            MatchProperties properties,
            @Value("${rutatur.internal-token:}") String internalToken,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this(buildRestClient(builder, properties), internalToken, circuitBreakerRegistry);
    }

    ItineraryInternalClient(
            RestClient restClient, String internalToken, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restClient = restClient;
        this.internalToken = internalToken;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
    }

    private static RestClient buildRestClient(RestClient.Builder builder, MatchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(trimSlash(properties.itineraryBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public InternalItineraryDto detail(UUID itineraryId) {
        InternalItineraryDto body = execute(() -> restClient.get()
                .uri("/internal/itineraries/{id}", itineraryId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(InternalItineraryDto.class));
        if (body == null) {
            throw new ResourceNotFoundException(
                    ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found");
        }
        return body;
    }

    public List<InternalItinerarySummary> summaries(String ownerUserId) {
        List<InternalItinerarySummary> body = execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/itineraries")
                        .queryParam("owner", ownerUserId)
                        .build())
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<InternalItinerarySummary>>() {
                }));
        return body == null ? List.of() : body;
    }

    private <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return circuitBreaker.executeSupplier(() -> executeWithRetry(call));
        } catch (CallNotPermittedException ex) {
            log.warn("Itinerary circuit is open; skipping the call");
            throw new UpstreamServiceException(
                    ErrorCode.UPSTREAM_UNAVAILABLE, "Itinerary service is unavailable", ex);
        }
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> call) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return call.get();
            } catch (RestClientException ex) {
                if (ex instanceof RestClientResponseException responseException
                        && responseException.getStatusCode().value() == 404) {
                    throw new ResourceNotFoundException(
                            ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found");
                }
                lastFailure = ex;
                if (!isRetryable(ex)) {
                    break;
                }
            }
            if (attempt == 1) {
                sleep();
            }
        }
        log.warn("Itinerary lookup failed: {}",
                lastFailure == null ? "unknown" : lastFailure.getMessage());
        throw new UpstreamServiceException(
                ErrorCode.UPSTREAM_UNAVAILABLE, "Itinerary service is unavailable", lastFailure);
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
}
