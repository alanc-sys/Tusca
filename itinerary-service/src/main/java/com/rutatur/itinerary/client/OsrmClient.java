package com.rutatur.itinerary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.util.GeoUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Cliente HTTP hacia OSRM Table API para cálculo de matriz de tiempos y distancias reales
 * por red vial, con fallback heurístico vía Haversine si OSRM no responde (RN-03).
 */
@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final RestClient restClient;
    private final String osrmBaseUrl;

    public OsrmClient(
            @Value("${osrm.api.url:https://router.project-osrm.org}") String osrmBaseUrl,
            @Value("${osrm.api.timeout-ms:5000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.osrmBaseUrl = osrmBaseUrl;
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @CircuitBreaker(name = "osrm", fallbackMethod = "fallbackMatrix")
    @Retry(name = "osrm")
    public TravelMatrix fetchMatrix(List<GeoPointDto> points, TransportMode mode) {
        String coords = points.stream()
                .map(p -> String.format(Locale.US, "%f,%f", p.longitude(), p.latitude()))
                .collect(Collectors.joining(";"));

        String url = String.format(
                "%s/table/v1/%s/%s?annotations=duration,distance",
                osrmBaseUrl,
                mode.getOsrmProfile(),
                coords
        );

        JsonNode body = restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);

        if (body == null || !"Ok".equalsIgnoreCase(body.path("code").asText())
                || !body.has("durations") || !body.has("distances")) {
            throw new IllegalStateException("Respuesta inválida de OSRM Table API");
        }

        int n = points.size();
        int[][] durationsMinutes = new int[n][n];
        double[][] distancesMeters = new double[n][n];

        JsonNode durationsNode = body.get("durations");
        JsonNode distancesNode = body.get("distances");

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double seconds = durationsNode.get(i).get(j).asDouble(0.0);
                double meters = distancesNode.get(i).get(j).asDouble(0.0);
                durationsMinutes[i][j] = (i == j) ? 0 : Math.max(1, (int) Math.ceil(seconds / 60.0));
                distancesMeters[i][j] = meters;
            }
        }

        return new TravelMatrix(durationsMinutes, distancesMeters, true);
    }

    public TravelMatrix fallbackMatrix(List<GeoPointDto> points, TransportMode mode, Throwable ex) {
        log.warn("OSRM no disponible ({}). Aplicando fallback heurístico Haversine (RN-03).", ex.getMessage());
        int n = points.size();
        int[][] durationsMinutes = new int[n][n];
        double[][] distancesMeters = new double[n][n];

        double metersPerMinute = switch (mode) {
            case WALKING -> 75.0;   // ~4.5 km/h
            case CYCLING -> 250.0;  // ~15 km/h
            case DRIVING -> 500.0;  // ~30 km/h urbano
        };

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    durationsMinutes[i][j] = 0;
                    distancesMeters[i][j] = 0.0;
                } else {
                    double meters = GeoUtils.haversineDistanceMeters(points.get(i), points.get(j));
                    distancesMeters[i][j] = Math.round(meters * 10.0) / 10.0;
                    durationsMinutes[i][j] = Math.max(1, (int) Math.ceil(meters / metersPerMinute));
                }
            }
        }

        return new TravelMatrix(durationsMinutes, distancesMeters, false);
    }

    public record TravelMatrix(int[][] durationsMinutes, double[][] distancesMeters, boolean osrmUsed) {
    }
}
