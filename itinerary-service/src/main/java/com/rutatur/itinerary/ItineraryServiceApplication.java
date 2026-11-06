package com.rutatur.itinerary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ItineraryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItineraryServiceApplication.class, args);
    }
}
