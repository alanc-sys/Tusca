package com.rutatur.gateway.web;

import com.rutatur.common.security.InternalHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes any client-supplied {@code X-Internal-Token} before a request is routed. Services trust
 * that header only on {@code /internal/**} calls, so it must never arrive from the public edge.
 *
 * <p>Implemented as a decorator (instead of the stock {@code RemoveRequestHeader} filter) because
 * the reactive request headers are read-only and mutating them fails at runtime.
 */
@Component
public class InternalHeaderStrippingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getHeaders().containsKey(InternalHeaders.INTERNAL_TOKEN)) {
            return chain.filter(exchange);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        headers.remove(InternalHeaders.INTERNAL_TOKEN);

        ServerHttpRequest stripped = new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.readOnlyHttpHeaders(headers);
            }
        };
        return chain.filter(exchange.mutate().request(stripped).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
