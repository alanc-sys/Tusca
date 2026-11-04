package com.rutatur.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.gateway.web.GatewayErrorWebExceptionHandler;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GatewayErrorHandlerTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final GatewayErrorWebExceptionHandler handler =
            new GatewayErrorWebExceptionHandler(objectMapper);

    @Test
    void notFoundRendersSharedEnvelope() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/unknown");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode body = body(exchange);
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.ROUTE_NOT_FOUND.name());
        assertThat(body.get("path").asText()).isEqualTo("/api/unknown");
        assertThat(body.get("message").asText()).doesNotContain("http://");
    }

    @Test
    void upstreamConnectionFailureBecomes503() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/pois/search");

        handler.handle(exchange, new ConnectException("Connection refused")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = body(exchange);
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE.name());
        assertThat(body.get("message").asText()).doesNotContain("Connection refused");
    }

    @Test
    void unexpectedFailureBecomes500WithoutLeakingDetails() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/pois/search");

        handler.handle(exchange, new IllegalStateException("jdbc:postgresql://secret"))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode body = body(exchange);
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(body.get("message").asText()).doesNotContain("jdbc");
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private JsonNode body(MockServerWebExchange exchange) throws Exception {
        return objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
    }
}
