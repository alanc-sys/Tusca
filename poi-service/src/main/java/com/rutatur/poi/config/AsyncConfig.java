package com.rutatur.poi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configura un pool acotado para refrescar consultas lentas a Overpass API
 * sin bloquear el hilo de respuesta HTTP ni saturar al proveedor externo.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "overpassRefreshExecutor")
    public Executor overpassRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("overpass-refresh-");
        executor.initialize();
        return executor;
    }
}
