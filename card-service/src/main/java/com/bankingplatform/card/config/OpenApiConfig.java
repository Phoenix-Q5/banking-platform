package com.bankingplatform.card.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cardServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Harbor Bank — Card Service")
                .description("Card issuance, freeze/unfreeze, and spending limits")
                .version("0.1.0"))
            .addServersItem(new Server().url("http://localhost:8080").description("API Gateway"))
            .addServersItem(new Server().url("http://localhost:8086").description("Direct service"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
