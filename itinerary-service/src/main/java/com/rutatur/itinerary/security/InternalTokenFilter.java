package com.rutatur.itinerary.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.security.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege /internal/** exigiendo la cabecera X-Internal-Token (RN-10).
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(
            @Value("${rutatur.internal-token}") String expectedToken,
            ObjectMapper objectMapper
    ) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedToken = request.getHeader(InternalHeaders.X_INTERNAL_TOKEN);
        if (!isValidToken(providedToken)) {
            response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = ApiError.of(
                    ErrorCode.FORBIDDEN,
                    "Acceso interno denegado: token interno inválido o ausente",
                    request.getRequestURI()
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValidToken(String providedToken) {
        if (providedToken == null || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
