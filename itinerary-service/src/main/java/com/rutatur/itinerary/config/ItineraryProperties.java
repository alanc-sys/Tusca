package com.rutatur.itinerary.config;

import java.time.Duration;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Generation and integration configuration. */
@ConfigurationProperties(prefix = "rutatur.itinerary")
public record ItineraryProperties(
        String poiBaseUrl,
        String osrmBaseUrl,
        LocalTime startClock,
        int maxCandidates,
        Duration connectTimeout,
        Duration readTimeout) {

    public ItineraryProperties {
        poiBaseUrl = poiBaseUrl == null ? "http://localhost:8081" : poiBaseUrl;
        osrmBaseUrl = osrmBaseUrl == null ? "http://localhost:5000" : osrmBaseUrl;
        startClock = startClock == null ? LocalTime.of(9, 0) : startClock;
        maxCandidates = maxCandidates <= 0 ? 50 : maxCandidates;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
