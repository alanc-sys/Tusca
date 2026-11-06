package com.rutatur.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ApiError;
import com.rutatur.common.error.ErrorCode;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Renders gateway-level failures as the shared {@code ApiError} envelope. Upstream URLs, SQL and
 * tokens are never included.
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        Failure failure = classify(ex);
        if (failure.code() == ErrorCode.INTERNAL_ERROR) {
            log.warn("Unhandled gateway failure", ex);
        }
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(failure.code().statusValue()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = serialize(ApiError.of(
                failure.code(), failure.message(), exchange.getRequest().getURI().getPath()));
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private Failure classify(Throwable ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        if (ex instanceof ResponseStatusException statusException) {
            if (statusException.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new Failure(ErrorCode.ROUTE_NOT_FOUND, "Resource not found");
            }
            if (statusException.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                return new Failure(ErrorCode.UPSTREAM_UNAVAILABLE, "Upstream service is unavailable");
            }
        }
        if (ex instanceof AuthenticationException || cause instanceof AuthenticationException) {
            return new Failure(ErrorCode.UNAUTHORIZED, "Unauthorized");
        }
        if (ex instanceof AccessDeniedException || cause instanceof AccessDeniedException) {
            return new Failure(ErrorCode.ACCESS_DENIED, "Access denied");
        }
        if (ex instanceof ServerWebInputException) {
            return new Failure(ErrorCode.VALIDATION_FAILED, "Malformed request");
        }
        if (isConnectivityFailure(ex) || isConnectivityFailure(cause)) {
            return new Failure(ErrorCode.UPSTREAM_UNAVAILABLE, "Upstream service is unavailable");
        }
        return new Failure(ErrorCode.INTERNAL_ERROR, "Internal error");
    }

    private static boolean isConnectivityFailure(Throwable ex) {
        return ex instanceof ConnectException
                || ex instanceof java.net.UnknownHostException
                || ex instanceof java.util.concurrent.TimeoutException
                || ex.getClass().getName().contains("WebClientRequestException");
    }

    private byte[] serialize(ApiError error) {
        try {
            return objectMapper.writeValueAsString(error).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            return ("{\"code\":\"" + error.code() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private record Failure(ErrorCode code, String message) {
    }
}
