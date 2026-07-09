package com.bankingplatform.opsagent.tools;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LokiQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(LokiQueryTool.class);

    private final WebClient webClient;

    public LokiQueryTool(WebClient.Builder builder, OpsAgentProperties properties) {
        this.webClient = builder.baseUrl(properties.getObservability().getLokiUrl()).build();
    }

    @Override
    public String name() {
        return "loki_query";
    }

    @Override
    public String description() {
        return "Query Loki logs with LogQL. Useful for error stacks, circuit breaker events, and failed request context.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "query", Map.of("type", "string", "description", "LogQL query, e.g. {service=\"transaction-service\"} |= \"ERROR\"", "required", true),
            "limit", Map.of("type", "integer", "description", "Max log lines (default 50)", "required", false),
            "minutesBack", Map.of("type", "integer", "description", "Lookback window in minutes (default 15)", "required", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        Object q = arguments.get("query");
        if (q == null || q.toString().isBlank()) {
            return ToolResult.fail("query is required");
        }
        int limit = intArg(arguments.get("limit"), 50);
        int minutesBack = intArg(arguments.get("minutesBack"), 15);
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(minutesBack));

        try {
            String uri = UriComponentsBuilder.fromPath("/loki/api/v1/query_range")
                .queryParam("query", q.toString())
                .queryParam("limit", limit)
                .queryParam("start", start.toEpochMilli() * 1_000_000L)
                .queryParam("end", end.toEpochMilli() * 1_000_000L)
                .build()
                .toUriString();

            JsonNode body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(15));

            if (body == null) {
                return ToolResult.fail("Empty response from Loki");
            }

            JsonNode result = body.path("data").path("result");
            List<String> lines = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode stream : result) {
                    JsonNode values = stream.path("values");
                    if (values.isArray()) {
                        for (JsonNode pair : values) {
                            if (pair.isArray() && pair.size() >= 2) {
                                lines.add(pair.get(1).asText());
                            }
                        }
                    }
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("lineCount", lines.size());
            data.put("lines", lines.size() > 40 ? lines.subList(0, 40) : lines);
            data.put("query", q.toString());

            return ToolResult.ok("Loki returned " + lines.size() + " log lines", data);
        } catch (Exception ex) {
            log.warn("loki_query failed: {}", ex.getMessage());
            return ToolResult.fail("Loki query error: " + ex.getMessage());
        }
    }

    private int intArg(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
