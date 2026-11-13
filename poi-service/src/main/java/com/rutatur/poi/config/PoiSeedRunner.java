package com.rutatur.poi.config;

import com.rutatur.poi.entity.PoiEntity;
import com.rutatur.poi.repository.PoiRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

/**
 * Siembra POIs reales iniciales en Córdoba si poi_db arranca vacía,
 * garantizando respaldo de caché local incluso si Overpass API está saturado.
 */
@Configuration
public class PoiSeedRunner {

    @Bean
    CommandLineRunner seedInitialPois(PoiRepository poiRepository) {
        return args -> {
            if (poiRepository.count() > 0) {
                return;
            }
            Instant now = Instant.now();
            poiRepository.saveAll(List.of(
                    new PoiEntity("node-seed-1", "Plaza San Martín", "park", -31.4167, -64.1836, "Independencia 50", 30, now),
                    new PoiEntity("node-seed-2", "Cabildo Histórico de Córdoba", "museum", -31.4165, -64.1841, "Independencia 30", 60, now),
                    new PoiEntity("node-seed-3", "Museo Genaro Pérez", "museum", -31.4139, -64.1872, "Av. General Paz 33", 60, now),
                    new PoiEntity("node-seed-4", "Paseo del Buen Pastor", "attraction", -31.4235, -64.1869, "Av. Hipólito Yrigoyen 325", 40, now),
                    new PoiEntity("node-seed-5", "Parque Sarmiento", "park", -31.4285, -64.1795, "Av. Deodoro Roca s/n", 45, now),
                    new PoiEntity("node-seed-6", "Café del Alba", "cafe", -31.4182, -64.1852, "9 de Julio 482", 30, now)
            ));
        };
    }
}
