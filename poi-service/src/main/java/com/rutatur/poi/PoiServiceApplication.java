package com.rutatur.poi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PoiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PoiServiceApplication.class, args);
    }
}
