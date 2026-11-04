package com.rutatur.match.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Share expiry, similarity threshold and outbound integration settings. */
@ConfigurationProperties(prefix = "rutatur.match")
public record MatchProperties(
        String itineraryBaseUrl,
        String keycloakAdminUrl,
        String keycloakRealm,
        String keycloakClientId,
        String keycloakClientSecret,
        Duration shareTtl,
        Duration connectTimeout,
        Duration readTimeout,
        BigDecimal matchThreshold) {

    public MatchProperties {
        itineraryBaseUrl = itineraryBaseUrl == null ? "http://localhost:8082" : itineraryBaseUrl;
        keycloakAdminUrl = keycloakAdminUrl == null ? "http://localhost:8180" : keycloakAdminUrl;
        keycloakRealm = keycloakRealm == null ? "rutatur-realm" : keycloakRealm;
        keycloakClientId = keycloakClientId == null ? "rutatur-match" : keycloakClientId;
        keycloakClientSecret = keycloakClientSecret == null ? "" : keycloakClientSecret;
        shareTtl = shareTtl == null ? Duration.ofDays(7) : shareTtl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        matchThreshold = matchThreshold == null ? new BigDecimal("0.35") : matchThreshold;
    }
}
