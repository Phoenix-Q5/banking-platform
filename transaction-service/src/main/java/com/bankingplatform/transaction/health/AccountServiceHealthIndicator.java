package com.bankingplatform.transaction.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class AccountServiceHealthIndicator implements HealthIndicator {

    private final WebClient webClient;

    public AccountServiceHealthIndicator(WebClient accountServiceWebClient) {
        this.webClient = accountServiceWebClient;
    }

    @Override
    public Health health() {
        try {
            String body = webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(2));

            boolean up = body != null && body.contains("\"status\":\"UP\"");
            return (up ? Health.up() : Health.down())
                .withDetail("downstream", "account-service")
                .build();
        } catch (Exception ex) {
            return Health.down(ex)
                .withDetail("downstream", "account-service")
                .withDetail("reason", ex.getMessage())
                .build();
        }
    }
}
