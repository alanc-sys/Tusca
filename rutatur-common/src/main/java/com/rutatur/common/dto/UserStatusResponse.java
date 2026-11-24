package com.rutatur.common.dto;

import java.util.UUID;

/** Estado resultante de una operación de bloqueo/desbloqueo administrativo (RN-17). */
public record UserStatusResponse(UUID userId, String username, String status) {
}
