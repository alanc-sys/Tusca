package com.rutatur.match;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(servers = @Server(url = "/", description = "Current Host / API Gateway"))
public class RouteMatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteMatchServiceApplication.class, args);
    }
}
