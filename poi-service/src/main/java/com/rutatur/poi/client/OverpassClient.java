package com.rutatur.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.config.PoiProperties;
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
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private final RestClient restClient;
    private final OverpassQueryBuilder queryBuilder;
    private final Duration retryBackoff;

    public OverpassClient(
            RestClient.Builder builder, PoiProperties properties, OverpassQueryBuilder queryBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(properties.overpassUrl())
                .requestFactory(requestFactory)
                .build();
        this.queryBuilder = queryBuilder;
        this.retryBackoff = Duration.ofMillis(500);
    }

    public JsonNode fetch(double lat, double lng, int radius, List<Category> categories) {
        String query = queryBuilder.build(lat, lng, radius, categories);
        RestClientException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("data", query);
                return restClient.post()
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
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
