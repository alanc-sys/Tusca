package com.rutatur.poi.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for the POI service. */
@ConfigurationProperties(prefix = "rutatur.poi")
public record PoiProperties(
        String overpassUrl,
        Duration cacheTtl,
        Duration cacheRetention,
        Duration connectTimeout,
        Duration readTimeout) {

    public PoiProperties {
        overpassUrl = overpassUrl == null ? "https://overpass-api.de/api/interpreter" : overpassUrl;
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(15) : cacheTtl;
        cacheRetention = cacheRetention == null ? Duration.ofDays(7) : cacheRetention;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
