package com.rutatur.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.security.KeycloakJwtRolesExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Configuración reactiva de seguridad del API Gateway:
 * valida JWT emitidos por Keycloak, bloquea /internal/** y estandariza errores 401/403 como ApiError.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private final ObjectMapper objectMapper;

    public GatewaySecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ServerAuthenticationEntryPoint unauthorizedHandler =
                (exchange, ex) -> writeApiError(exchange, ErrorCode.UNAUTHORIZED, "Autenticación requerida o token inválido");

        ServerAccessDeniedHandler forbiddenHandler =
                (exchange, ex) -> writeApiError(exchange, ErrorCode.FORBIDDEN, "No tenés permisos para acceder a este recurso");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        // Aislamiento estricto de endpoints internos (RN-10)
                        .pathMatchers("/internal/**").denyAll()

                        // Endpoints públicos y documentación OpenAPI
                        .pathMatchers(HttpMethod.GET, "/api/pois/search", "/api/pois/*").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/itinerary/generate").permitAll()
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/*/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Endpoints administrativos (F-08)
                        .pathMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")

                        // Endpoints de usuarios autenticados (F-03 a F-07)
                        .pathMatchers("/api/itineraries/**", "/api/routes/**").hasRole("USER")

                        .anyExchange().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(forbiddenHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(forbiddenHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        Converter<Jwt, AbstractAuthenticationToken> delegate = jwt -> {
            List<GrantedAuthority> authorities = KeycloakJwtRolesExtractor.extractRoles(jwt.getClaims())
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    private Mono<Void> writeApiError(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError apiError = ApiError.of(
                errorCode,
                message,
                exchange.getRequest().getPath().value()
        );

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(apiError);
        } catch (JsonProcessingException e) {
            payload = ("{\"status\":" + errorCode.getHttpStatus() + ",\"code\":\"" + errorCode.name() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(payload);
        return response.writeWith(Mono.just(buffer));
    }
}
