package com.rutatur.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ApiError;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.security.RealmRoleConverter;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway authorization matrix. Unmatched paths fall through to the routing layer, which answers
 * {@code 404}; {@code /internal/**} is explicitly permitted so it reaches that layer and can never
 * be forwarded because no route exists for it.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**")
                        .permitAll()
                        .pathMatchers("/internal/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/pois/search").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/itinerary/generate").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").hasRole("USER")
                        .anyExchange().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
        return converter;
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter());
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${rutatur.security.jwt-issuer-uri}") String issuerUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(
                        issuerUri + "/protocol/openid-connect/certs")
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (exchange, ex) -> write(
                exchange, objectMapper, ErrorCode.UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public ServerAccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (exchange, ex) -> write(
                exchange, objectMapper, ErrorCode.ACCESS_DENIED, "Access denied");
    }

    private Mono<Void> write(
            ServerWebExchange exchange, ObjectMapper objectMapper, ErrorCode code, String message) {
        exchange.getResponse().setStatusCode(
                org.springframework.http.HttpStatusCode.valueOf(code.statusValue()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = toBytes(objectMapper, ApiError.of(
                code, message, exchange.getRequest().getURI().getPath()));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static byte[] toBytes(ObjectMapper objectMapper, ApiError error) {
        try {
            return objectMapper.writeValueAsString(error).getBytes(StandardCharsets.UTF_8);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return ("{\"code\":\"" + error.code() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
