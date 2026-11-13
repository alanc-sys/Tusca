package com.rutatur.gateway.controller;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.util.Map;

/**
 * Endpoints administrativos del Gateway (F-08), protegidos con ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RouteLocator routeLocator;

    public AdminController(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @GetMapping("/routes")
    public Flux<RouteSummary> listRoutes() {
        return routeLocator.getRoutes()
                .map(this::toRouteSummary);
    }

    @GetMapping("/metrics")
    public Mono<Map<String, Object>> getGatewayMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return routeLocator.getRoutes()
                .count()
                .map(routeCount -> Map.of(
                        "service", "api-gateway",
                        "status", "UP",
                        "configuredRoutes", routeCount,
                        "jvmUsedMemoryBytes", usedMemoryBytes,
                        "uptimeSeconds", uptimeSeconds
                ));
    }

    private RouteSummary toRouteSummary(Route route) {
        return new RouteSummary(
                route.getId(),
                route.getUri().toString(),
                route.getPredicate().toString()
        );
    }

    public record RouteSummary(String id, String uri, String predicate) {
    }
}
