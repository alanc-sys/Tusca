package com.rutatur.gateway.filter;

import com.rutatur.common.security.InternalHeaders;
import com.rutatur.common.security.KeycloakJwtRolesExtractor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(InternalHeaders.X_INTERNAL_TOKEN);
                    headers.remove(InternalHeaders.X_USER_ID);
                    headers.remove(InternalHeaders.X_USER_ROLES);
                })
                .build();

        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    String userId = auth.getToken().getSubject();
                    String roles = String.join(",", KeycloakJwtRolesExtractor.extractRoles(auth.getToken().getClaims()));
                    ServerHttpRequest enrichedRequest = sanitizedExchange.getRequest().mutate()
                            .header(InternalHeaders.X_USER_ID, userId)
                            .header(InternalHeaders.X_USER_ROLES, roles)
                            .build();
                    return sanitizedExchange.mutate().request(enrichedRequest).build();
                })
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
