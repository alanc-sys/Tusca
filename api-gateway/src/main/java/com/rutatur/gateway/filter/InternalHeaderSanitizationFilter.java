package com.rutatur.gateway.filter;

import com.rutatur.common.security.InternalHeaders;
import com.rutatur.common.security.KeycloakJwtRolesExtractor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Elimina cabeceras internas enviadas por clientes externos (anti-spoofing)
 * y propaga X-User-Id / X-User-Roles cuando la petición posee un JWT válido.
 */
@Component
public class InternalHeaderSanitizationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> buildSanitizedExchange(exchange, auth))
                .defaultIfEmpty(buildSanitizedExchange(exchange, null))
                .flatMap(chain::filter);
    }

    private ServerWebExchange buildSanitizedExchange(ServerWebExchange exchange, JwtAuthenticationToken auth) {
        HttpHeaders sanitizedHeaders = new HttpHeaders();
        sanitizedHeaders.addAll(exchange.getRequest().getHeaders());
        sanitizedHeaders.remove(InternalHeaders.X_INTERNAL_TOKEN);
        sanitizedHeaders.remove(InternalHeaders.X_USER_ID);
        sanitizedHeaders.remove(InternalHeaders.X_USER_ROLES);

        if (auth != null) {
            String userId = auth.getToken().getSubject();
            String roles = String.join(",", KeycloakJwtRolesExtractor.extractRoles(auth.getToken().getClaims()));
            sanitizedHeaders.set(InternalHeaders.X_USER_ID, userId);
            sanitizedHeaders.set(InternalHeaders.X_USER_ROLES, roles);
        }

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return sanitizedHeaders;
            }
        };

        return exchange.mutate().request(decoratedRequest).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
