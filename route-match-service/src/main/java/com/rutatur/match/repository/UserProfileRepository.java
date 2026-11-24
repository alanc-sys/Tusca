package com.rutatur.match.repository;

import com.rutatur.match.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistencia de perfiles sincronizados desde Keycloak (RN-13). */
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {
}
