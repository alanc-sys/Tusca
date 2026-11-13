package com.rutatur.common.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extrae y normaliza roles desde los claims de un JWT emitido por Keycloak (realm_access.roles)
 * sin acoplarse a Spring Security Servlet ni WebFlux.
 */
public final class KeycloakJwtRolesExtractor {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private KeycloakJwtRolesExtractor() {
    }

    public static List<String> extractRoles(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return Collections.emptyList();
        }

        Object realmAccessObj = claims.get(REALM_ACCESS_CLAIM);
        if (!(realmAccessObj instanceof Map<?, ?> realmAccess)) {
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get(ROLES_KEY);
        if (!(rolesObj instanceof Collection<?> rawRoles)) {
            return Collections.emptyList();
        }

        return rawRoles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(KeycloakJwtRolesExtractor::normalizeRole)
                .distinct()
                .toList();
    }

    private static String normalizeRole(String role) {
        return role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
    }
}
