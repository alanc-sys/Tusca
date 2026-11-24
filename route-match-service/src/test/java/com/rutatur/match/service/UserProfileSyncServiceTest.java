package com.rutatur.match.service;

import com.rutatur.match.entity.UserProfileEntity;
import com.rutatur.match.entity.UserProfileStatus;
import com.rutatur.match.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileSyncServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileSyncService service() {
        return new UserProfileSyncService(userProfileRepository);
    }

    @Test
    void createsActiveProfileFromJwtClaims() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        service().sync(jwt(userId.toString(), "user1", "user1@rutatur.com"));

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertEquals(userId, saved.getId());
        assertEquals("user1", saved.getUsername());
        assertEquals("user1@rutatur.com", saved.getEmail());
        assertEquals(UserProfileStatus.ACTIVE, saved.getStatus());
        assertEquals(0, saved.getRatingsCount());
        assertEquals(false, saved.isAdmin());
    }

    @Test
    void marksProfileAsAdminWhenJwtHasAdminRole() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        Jwt adminJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("preferred_username", "admin1")
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER", "ROLE_ADMIN")))
                .build();

        service().sync(adminJwt);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertEquals(true, captor.getValue().isAdmin());
    }

    @Test
    void marksProfileAsAdminWhenJwtHasUnprefixedAdminRole() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        Jwt adminJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("preferred_username", "admin1")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        service().sync(adminJwt);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertEquals(true, captor.getValue().isAdmin());
    }

    @Test
    void updatesIdentityButKeepsBlockedStatusAndReputation() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity(userId, "old", "old@rutatur.com");
        existing.setStatus(UserProfileStatus.BLOCKED);
        existing.setRatingsCount(7);
        existing.setAdmin(true);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        service().sync(jwt(userId.toString(), "user1", "user1@rutatur.com"));

        assertEquals("user1", existing.getUsername());
        assertEquals("user1@rutatur.com", existing.getEmail());
        assertEquals(UserProfileStatus.BLOCKED, existing.getStatus());
        assertEquals(7, existing.getRatingsCount());
        assertEquals(false, existing.isAdmin());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void fallsBackToSubjectWhenPreferredUsernameIsMissing() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .build();

        service().sync(jwt);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertEquals(userId.toString(), captor.getValue().getUsername());
        assertNull(captor.getValue().getEmail());
    }

    @Test
    void ignoresJwtWithoutSubject() {
        service().sync(Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("iss", "rutatur")
                .build());

        verify(userProfileRepository, never()).findById(any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void ignoresSubjectThatIsNotUuid() {
        service().sync(jwt("not-a-uuid", "user1", "user1@rutatur.com"));

        verify(userProfileRepository, never()).findById(any());
        verify(userProfileRepository, never()).save(any());
    }

    private static Jwt jwt(String sub, String username, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", sub)
                .claim("preferred_username", username)
                .claim("email", email)
                .build();
    }
}
