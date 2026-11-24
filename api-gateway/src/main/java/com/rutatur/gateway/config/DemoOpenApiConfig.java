package com.rutatur.gateway.config;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documenta el header opcional {@code X-Demo-User} en todas las operaciones de Swagger UI. */
@Configuration
public class DemoOpenApiConfig {

    @Bean
    public OperationCustomizer demoUserHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean alreadyPresent = operation.getParameters() != null
                    && operation.getParameters().stream()
                            .anyMatch(p -> "X-Demo-User".equalsIgnoreCase(p.getName()));
            if (!alreadyPresent) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("X-Demo-User")
                        .required(false)
                        .description("Usuario demo (user1, user2, admin1). Solo se aplica con el perfil 'demo' "
                                + "y cuando la petición no trae Authorization.")
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }
}
