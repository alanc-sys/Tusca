package com.rutatur.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRoleConverterTest {

    private final RealmRoleConverter converter = new RealmRoleConverter();

    @Test
    void mapsRealmRolesToAuthorities() {
        Jwt jwt = jwtWith(Map.of("roles", List.of("ROLE_USER")));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void missingClaimYieldsNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user1")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwtWith(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user1")
                .claim(RealmRoleConverter.REALM_ACCESS_CLAIM, realmAccess)
                .build();
    }
}
