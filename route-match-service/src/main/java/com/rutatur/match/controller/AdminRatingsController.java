package com.rutatur.match.controller;

import com.rutatur.common.dto.AdminRatingsReportResponse;
import com.rutatur.common.dto.UserStatusResponse;
import com.rutatur.match.entity.UserProfileStatus;
import com.rutatur.match.service.AdminRatingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints de auditoría y bloqueo, restringidos a {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Ratings", description = "Informe de calificaciones y bloqueo de usuarios")
public class AdminRatingsController {

    private final AdminRatingsService adminRatingsService;

    public AdminRatingsController(AdminRatingsService adminRatingsService) {
        this.adminRatingsService = adminRatingsService;
    }

    @GetMapping("/ratings/report")
    @Operation(summary = "Métricas globales y ranking de usuarios por reputación (RN-18)")
    public AdminRatingsReportResponse report() {
        return adminRatingsService.report();
    }

    @PostMapping("/users/{userId}/block")
    @Operation(summary = "Bloquea una cuenta de usuario (RN-17)")
    public UserStatusResponse blockUser(@PathVariable UUID userId) {
        return adminRatingsService.changeStatus(userId, UserProfileStatus.BLOCKED);
    }

    @PostMapping("/users/{userId}/unblock")
    @Operation(summary = "Desbloquea una cuenta de usuario (RN-17)")
    public UserStatusResponse unblockUser(@PathVariable UUID userId) {
        return adminRatingsService.changeStatus(userId, UserProfileStatus.ACTIVE);
    }
}
