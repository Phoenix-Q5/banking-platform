package com.bankingplatform.opsagent.tools;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PrometheusQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryTool.class);

    private final WebClient webClient;

    public PrometheusQueryTool(WebClient.Builder builder, OpsAgentProperties properties) {
        this.webClient = builder.baseUrl(properties.getObservability().getPrometheusUrl()).build();
    }

    @Override
    public String name() {
        return "prometheus_query";
    }

    @Override
    public String description() {
        return "Run a PromQL instant query against Prometheus. Use for error rates, latency, up targets, circuit breaker state.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "query", Map.of("type", "string", "description", "PromQL expression", "required", true),
            "timeoutSeconds", Map.of("type", "integer", "description", "Optional timeout", "required", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        Object q = arguments.get("query");
        if (q == null || q.toString().isBlank()) {
            return ToolResult.fail("query is required");
        }
        String query = q.toString();
        try {
            String uri = UriComponentsBuilder.fromPath("/api/v1/query")
                .queryParam("query", query)
                .build()
                .toUriString();

            JsonNode body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));

            if (body == null) {
                return ToolResult.fail("Empty response from Prometheus");
            }

            String status = body.path("status").asText();
            if (!"success".equals(status)) {
                return ToolResult.fail("Prometheus query failed: " + body.path("error").asText("unknown"));
            }

            JsonNode result = body.path("data").path("result");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resultType", body.path("data").path("resultType").asText());
            data.put("result", result);
            data.put("query", query);

            int series = result.isArray() ? result.size() : 0;
            return ToolResult.ok("Prometheus returned " + series + " series for query", data);
        } catch (Exception ex) {
            log.warn("prometheus_query failed: {}", ex.getMessage());
            return ToolResult.fail("Prometheus query error: " + ex.getMessage());
        }
    }
}
