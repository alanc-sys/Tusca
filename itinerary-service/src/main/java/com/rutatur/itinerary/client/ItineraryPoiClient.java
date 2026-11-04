package com.rutatur.itinerary.client;

import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.itinerary.config.ItineraryProperties;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Candidate lookup through poi-service's internal endpoint; never calls Overpass directly. */
@Component
public class ItineraryPoiClient {

    private static final Logger log = LoggerFactory.getLogger(ItineraryPoiClient.class);

    private final RestClient restClient;
    private final String internalToken;
    private final Duration retryBackoff = Duration.ofMillis(500);

    public ItineraryPoiClient(
            RestClient.Builder builder,
            ItineraryProperties properties,
            @Value("${rutatur.internal-token:}") String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(trimSlash(properties.poiBaseUrl()))
                .requestFactory(requestFactory)
                .build();
        this.internalToken = internalToken;
    }

    public PoiSearchResponse candidates(double lat, double lng, int radius, List<String> categories) {
        String categoryParam = categories == null ? "" : String.join(",", categories);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                PoiSearchResponse response = restClient.get()
                        .uri(uri -> uri.path("/internal/pois/candidates")
                                .queryParam("lat", lat)
                                .queryParam("lng", lng)
                                .queryParam("radius", radius)
                                .queryParamIfPresent(
                                        "categories",
                                        categoryParam.isBlank()
                                                ? java.util.Optional.empty()
                                                : java.util.Optional.of(categoryParam))
                                .build())
                        .header("X-Internal-Token", internalToken)
                        .retrieve()
                        .body(PoiSearchResponse.class);
                return response == null ? new PoiSearchResponse(List.of(), 0) : response;
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
        log.warn("Candidate lookup failed: {}", lastFailure == null ? "unknown" : lastFailure.getMessage());
        throw new UpstreamServiceException(
                ErrorCode.UPSTREAM_UNAVAILABLE, "POI service is unavailable", lastFailure);
    }

    private void sleep() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Kept for readability of generated query strings in tests. */
    static String joinCategories(List<String> categories) {
        return categories.stream().collect(Collectors.joining(","));
    }
}
