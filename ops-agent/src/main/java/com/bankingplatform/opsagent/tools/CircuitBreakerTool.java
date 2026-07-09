package com.bankingplatform.opsagent.tools;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CircuitBreakerTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerTool.class);

    private final WebClient.Builder builder;
    private final OpsAgentProperties properties;

    public CircuitBreakerTool(WebClient.Builder builder, OpsAgentProperties properties) {
        this.builder = builder;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "circuit_breaker_state";
    }

    @Override
    public String description() {
        return "Fetch Resilience4j circuit breaker state/events from a service actuator endpoint (transaction-service or api-gateway).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "service", Map.of("type", "string", "description", "Service exposing circuitbreakers actuator", "required", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        Object serviceArg = arguments.get("service");
        if (serviceArg == null || serviceArg.toString().isBlank()) {
            return ToolResult.fail("service is required");
        }
        String service = serviceArg.toString();
        String baseUrl = properties.getServices().get(service);
        if (baseUrl == null) {
            return ToolResult.fail("Unknown service: " + service);
        }

        try {
            WebClient client = builder.baseUrl(baseUrl).build();
            JsonNode breakers = safeGet(client, "/actuator/circuitbreakers");
            JsonNode events = safeGet(client, "/actuator/circuitbreakerevents");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("service", service);
            data.put("circuitbreakers", breakers);
            data.put("recentEvents", events);

            String summary = "Fetched circuit breaker state from " + service;
            if (breakers != null) {
                summary += " (" + breakers.path("circuitBreakers").size() + " breakers)";
            }
            return ToolResult.ok(summary, data);
        } catch (Exception ex) {
            log.warn("circuit_breaker_state failed: {}", ex.getMessage());
            return ToolResult.fail("Circuit breaker lookup failed: " + ex.getMessage());
        }
    }

    private JsonNode safeGet(WebClient client, String path) {
        try {
            return client.get().uri(path).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(5));
        } catch (Exception ex) {
            return null;
        }
    }
}
