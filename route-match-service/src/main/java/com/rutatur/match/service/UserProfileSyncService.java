package com.rutatur.match.service;

import com.rutatur.match.entity.UserProfileEntity;
import com.rutatur.match.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * RN-13: garantiza que todo usuario autenticado tenga un {@code user_profile} local.
 *
 * <p>Usa {@code sub} como identificador, {@code preferred_username} como nombre de usuario y
 * {@code email}. En el alta el estado es {@code ACTIVE}; en actualizaciones posteriores se
 * conservan el estado y la reputación, de modo que un bloqueo administrativo (RN-17) no se
 * deshace en la próxima petición.
 */
@Service
public class UserProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileSyncService.class);
    private static final int USERNAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 255;

    private final UserProfileRepository userProfileRepository;

    public UserProfileSyncService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public void sync(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring JIT profile sync: JWT sub is not a UUID ({})", jwt.getSubject());
            return;
        }

        String username = valueOrDefault(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        boolean admin = hasAdminRole(jwt);

        userProfileRepository.findById(userId).ifPresentOrElse(
                profile -> {
                    profile.setUsername(trim(username, USERNAME_MAX_LENGTH));
                    profile.setEmail(trim(email, EMAIL_MAX_LENGTH));
                    profile.setAdmin(admin);
                },
                () -> {
                    UserProfileEntity profile = new UserProfileEntity(
                            userId,
                            trim(username, USERNAME_MAX_LENGTH),
                            trim(email, EMAIL_MAX_LENGTH));
                    profile.setAdmin(admin);
                    userProfileRepository.save(profile);
                });
    }

    private static boolean hasAdminRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return false;
        }
        return roles.contains("ROLE_ADMIN");
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
