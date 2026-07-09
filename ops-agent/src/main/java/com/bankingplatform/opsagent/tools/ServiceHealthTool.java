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
public class ServiceHealthTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthTool.class);

    private final WebClient.Builder builder;
    private final OpsAgentProperties properties;

    public ServiceHealthTool(WebClient.Builder builder, OpsAgentProperties properties) {
        this.builder = builder;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "service_health";
    }

    @Override
    public String description() {
        return "Fetch Spring Boot /actuator/health for a known platform service (api-gateway, account-service, transaction-service). Includes DB and downstream indicators.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "service", Map.of(
                "type", "string",
                "description", "One of: " + String.join(", ", properties.getServices().keySet()),
                "required", true
            )
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
            return ToolResult.fail("Unknown service '" + service + "'. Known: " + properties.getServices().keySet());
        }

        try {
            JsonNode body = builder.baseUrl(baseUrl).build()
                .get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));

            if (body == null) {
                return ToolResult.fail("Empty health response from " + service);
            }

            String status = body.path("status").asText("UNKNOWN");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("service", service);
            data.put("status", status);
            data.put("details", body);

            return ToolResult.ok(service + " health status=" + status, data);
        } catch (Exception ex) {
            log.warn("service_health failed for {}: {}", service, ex.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("service", service);
            data.put("status", "UNREACHABLE");
            data.put("error", ex.getMessage());
            return new ToolResult(false, service + " is unreachable: " + ex.getMessage(), data);
        }
    }
}
