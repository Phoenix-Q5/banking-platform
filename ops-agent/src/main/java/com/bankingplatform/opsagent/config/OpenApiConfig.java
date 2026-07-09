package com.bankingplatform.opsagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI opsAgentOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Harbor Bank — Ops Agent")
                .description("AI monitoring, incident investigation, chat, and Alertmanager webhooks")
                .version("0.1.0"))
            .addServersItem(new Server().url("http://localhost:8085").description("Ops Agent"));
    }
}
