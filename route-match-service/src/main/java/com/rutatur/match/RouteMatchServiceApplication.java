package com.rutatur.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RouteMatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteMatchServiceApplication.class, args);
    }
}
