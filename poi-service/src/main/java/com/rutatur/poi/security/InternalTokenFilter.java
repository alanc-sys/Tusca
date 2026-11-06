package com.rutatur.poi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ApiError;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.security.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /internal/**} with a shared token compared in constant time.
 * Registered for every request but only applies to internal paths.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(
            @Value("${rutatur.internal-token:}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(InternalHeaders.INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(InternalHeaders.INTERNAL_TOKEN);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, providedBytes)) {
            response.setStatus(ErrorCode.INTERNAL_AUTH_REQUIRED.statusValue());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiError.of(
                            ErrorCode.INTERNAL_AUTH_REQUIRED,
                            "Internal authentication required",
                            request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
