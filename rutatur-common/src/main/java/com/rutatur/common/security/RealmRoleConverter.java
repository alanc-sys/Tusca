package com.rutatur.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps Keycloak's {@code realm_access.roles} claim to Spring Security authorities.
 *
 * <p>Keycloak emits already-prefixed roles ({@code ROLE_USER}, {@code ROLE_ADMIN}), so the values
 * are used verbatim; {@code hasRole("USER")} then matches {@code ROLE_USER}.
 */
public class RealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String REALM_ACCESS_CLAIM = "realm_access";
    public static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get(ROLES_CLAIM);
        if (!(roles instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(role -> !role.isBlank())
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
