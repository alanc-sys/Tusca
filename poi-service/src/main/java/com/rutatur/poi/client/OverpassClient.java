package com.rutatur.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.poi.entity.PoiEntity;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Cliente HTTP hacia Overpass API (OpenStreetMap) con soporte de mirrors de respaldo
 * y resiliencia vía Resilience4j.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private static final Map<String, Integer> STAY_MINUTES_BY_CATEGORY = Map.ofEntries(
            Map.entry("museum", 60),
            Map.entry("gallery", 45),
            Map.entry("park", 45),
            Map.entry("attraction", 40),
            Map.entry("cafe", 30),
            Map.entry("restaurant", 45),
            Map.entry("viewpoint", 20),
            Map.entry("monument", 20),
            Map.entry("memorial", 20)
    );

    private final RestClient restClient;
    private final List<String> overpassUrls;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OverpassClient(
            @Value("${overpass.api.url:https://overpass-api.de/api/interpreter}") String overpassUrlsRaw,
            @Value("${overpass.api.timeout-ms:50000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.overpassUrls = Arrays.stream(overpassUrlsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "RutaTur-POI-Service/1.0 (+https://rutatur.local)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @CircuitBreaker(name = "overpass")
    @Retry(name = "overpass")
    public List<PoiEntity> fetchPois(double lat, double lon, int radiusMeters) {
        String query = buildOverpassQuery(lat, lon, radiusMeters);
        RuntimeException lastError = null;

        for (String url : overpassUrls) {
            try {
                byte[] rawBytes = restClient.post()
                        .uri(url)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(query)
                        .retrieve()
                        .body(byte[].class);

                if (rawBytes != null && rawBytes.length > 0) {
                    String rawBody = decodePayload(rawBytes);
                    JsonNode response = objectMapper.readTree(rawBody);
                    if (response != null && response.has("elements")) {
                        return parseElements(response.get("elements"));
                    }
                }
            } catch (Exception ex) {
                log.warn("Overpass mirror {} falló: {}", url, ex.getMessage());
                lastError = new RutaturException(
                        ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "No se pudo consultar Overpass API: " + ex.getMessage()
                );
            }
        }

        throw lastError != null
                ? lastError
                : new RutaturException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Respuesta inesperada de Overpass API");
    }

    private String buildOverpassQuery(double lat, double lon, int radiusMeters) {
        return String.format(
                Locale.US,
                """
                [out:json][timeout:25];
                (
                  node["tourism"~"museum|attraction|viewpoint|gallery"](around:%d,%f,%f);
                  node["leisure"="park"](around:%d,%f,%f);
                  node["amenity"~"cafe|restaurant"](around:%d,%f,%f);
                  node["historic"~"monument|memorial"](around:%d,%f,%f);
                );
                out body 60;
                """,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon
        );
    }

    private List<PoiEntity> parseElements(JsonNode elements) {
        List<PoiEntity> result = new ArrayList<>();
        Instant now = Instant.now();

        for (JsonNode element : elements) {
            JsonNode tags = element.get("tags");
            if (tags == null || !tags.has("name")) {
                continue;
            }
            if (!element.has("lat") || !element.has("lon")) {
                continue;
            }

            String type = element.path("type").asText("node");
            long osmId = element.path("id").asLong();
            String id = type + "-" + osmId;
            String name = tags.get("name").asText();
            String category = resolveCategory(tags);
            String address = resolveAddress(tags);
            int stayMinutes = STAY_MINUTES_BY_CATEGORY.getOrDefault(category, 30);

            result.add(new PoiEntity(
                    id,
                    name,
                    category,
                    element.get("lat").asDouble(),
                    element.get("lon").asDouble(),
                    address,
                    stayMinutes,
                    now
            ));
        }

        return result;
    }

    private String resolveCategory(JsonNode tags) {
        if (tags.has("tourism")) {
            return tags.get("tourism").asText().toLowerCase(Locale.ROOT);
        }
        if (tags.has("leisure")) {
            return tags.get("leisure").asText().toLowerCase(Locale.ROOT);
        }
        if (tags.has("amenity")) {
            return tags.get("amenity").asText().toLowerCase(Locale.ROOT);
        }
        if (tags.has("historic")) {
            return tags.get("historic").asText().toLowerCase(Locale.ROOT);
        }
        return "attraction";
    }

    private String resolveAddress(JsonNode tags) {
        String street = tags.path("addr:street").asText("");
        String number = tags.path("addr:housenumber").asText("");
        if (!street.isBlank() && !number.isBlank()) {
            return street + " " + number;
        }
        if (!street.isBlank()) {
            return street;
        }
        return tags.path("addr:city").asText("Sin dirección registrada");
    }

    private String decodePayload(byte[] rawBytes) throws IOException {
        if (rawBytes.length >= 2 && rawBytes[0] == (byte) 0x1f && rawBytes[1] == (byte) 0x8b) {
            try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(rawBytes))) {
                return new String(gzipStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(rawBytes, StandardCharsets.UTF_8);
    }
}
