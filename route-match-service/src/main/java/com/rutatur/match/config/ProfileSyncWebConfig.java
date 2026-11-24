package com.rutatur.match.config;

import com.rutatur.match.security.JwtProfileSyncInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra el interceptor de sincronización JIT para todos los handlers MVC. */
@Configuration
public class ProfileSyncWebConfig implements WebMvcConfigurer {

    private final JwtProfileSyncInterceptor interceptor;

    public ProfileSyncWebConfig(JwtProfileSyncInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
