package com.rutatur.match.security;

import com.rutatur.match.service.UserProfileSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Ejecuta la sincronización JIT (RN-13) con el JWT ya autenticado por Spring Security. */
@Component
public class JwtProfileSyncInterceptor implements HandlerInterceptor {

    private final UserProfileSyncService userProfileSyncService;

    public JwtProfileSyncInterceptor(UserProfileSyncService userProfileSyncService) {
        this.userProfileSyncService = userProfileSyncService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            userProfileSyncService.sync(jwtAuthentication.getToken());
        }
        return true;
    }
}
