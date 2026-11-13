package com.rutatur.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakJwtRolesExtractorTest {

    @Test
    void extractsAndNormalizesRealmRoles() {
        Map<String, Object> claims = Map.of(
                "realm_access", Map.of("roles", List.of("ROLE_USER", "ADMIN", "ROLE_USER", " "))
        );

        List<String> roles = KeycloakJwtRolesExtractor.extractRoles(claims);

        assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), roles);
    }

    @Test
    void returnsEmptyListWhenClaimsMissingOrMalformed() {
        assertTrue(KeycloakJwtRolesExtractor.extractRoles(null).isEmpty());
        assertTrue(KeycloakJwtRolesExtractor.extractRoles(Map.of()).isEmpty());
        assertTrue(KeycloakJwtRolesExtractor.extractRoles(Map.of("realm_access", "invalid")).isEmpty());
    }
}
