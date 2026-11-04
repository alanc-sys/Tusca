package com.rutatur.itinerary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.itinerary.config.ItineraryProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OSRM {@code /table} integration. Seconds are rounded half-up to minutes and meters to integers;
 * a {@code null} cell becomes {@link RoutingMatrix#UNREACHABLE}. Euclidean estimates are never used.
 */
@Component
public class OsrmTableClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmTableClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final Duration retryBackoff = Duration.ofMillis(300);

    public OsrmTableClient(RestClient.Builder builder, ItineraryProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.requestFactory(requestFactory).build();
        this.baseUrl = trimSlash(properties.osrmBaseUrl());
    }

    public RoutingMatrix table(TransportMode mode, GeoPointDto start, List<PoiDto> orderedCandidates) {
        String coordinates = coordinates(start, orderedCandidates);
        String url = baseUrl + "/table/v1/" + profile(mode) + "/" + coordinates
                + "?annotations=duration,distance";

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                JsonNode body = restClient.get().uri(URI.create(url)).retrieve().body(JsonNode.class);
                if (body == null) {
                    throw new UpstreamServiceException(
                            ErrorCode.ROUTING_PROVIDER_UNAVAILABLE, "Routing provider returned no matrix");
                }
                return new RoutingMatrix(
                        minutesMatrix(body.path("durations")), metersMatrix(body.path("distances")));
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
        log.warn("Routing matrix failed: {}", lastFailure == null ? "unknown" : lastFailure.getMessage());
        throw new UpstreamServiceException(
                ErrorCode.ROUTING_PROVIDER_UNAVAILABLE, "Routing provider is unavailable", lastFailure);
    }

    static String profile(TransportMode mode) {
        return mode == TransportMode.WALKING ? "foot" : "driving";
    }

    private static String coordinates(GeoPointDto start, List<PoiDto> candidates) {
        StringBuilder builder = new StringBuilder(coordinate(start.longitude(), start.latitude()));
        for (PoiDto poi : candidates) {
            builder.append(';')
                    .append(coordinate(poi.location().longitude(), poi.location().latitude()));
        }
        return builder.toString();
    }

    private static String coordinate(double lng, double lat) {
        return String.format(Locale.ROOT, "%.6f,%.6f", lng, lat);
    }

    private static int[][] minutesMatrix(JsonNode durations) {
        return matrix(durations, true);
    }

    private static int[][] metersMatrix(JsonNode distances) {
        return matrix(distances, false);
    }

    private static int[][] matrix(JsonNode node, boolean secondsToMinutes) {
        if (node == null || !node.isArray()) {
            return new int[0][0];
        }
        int[][] result = new int[node.size()][];
        for (int i = 0; i < node.size(); i++) {
            JsonNode row = node.get(i);
            if (row == null || !row.isArray()) {
                result[i] = new int[0];
                continue;
            }
            result[i] = new int[row.size()];
            for (int j = 0; j < row.size(); j++) {
                JsonNode cell = row.get(j);
                if (cell == null || cell.isNull() || !cell.isNumber()) {
                    result[i][j] = RoutingMatrix.UNREACHABLE;
                } else if (secondsToMinutes) {
                    result[i][j] = (int) Math.round(cell.asDouble() / 60.0);
                } else {
                    result[i][j] = (int) Math.round(cell.asDouble());
                }
            }
        }
        return result;
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
}
