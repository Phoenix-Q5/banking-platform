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
public class TempoTraceTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(TempoTraceTool.class);

    private final WebClient webClient;

    public TempoTraceTool(WebClient.Builder builder, OpsAgentProperties properties) {
        this.webClient = builder.baseUrl(properties.getObservability().getTempoUrl()).build();
    }

    @Override
    public String name() {
        return "tempo_search";
    }

    @Override
    public String description() {
        return "Search recent traces in Tempo by service name. Use to find slow or error spans during an incident.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "service", Map.of("type", "string", "description", "Service name tag, e.g. transaction-service", "required", true),
            "minutesBack", Map.of("type", "integer", "description", "Lookback minutes (default 30)", "required", false),
            "limit", Map.of("type", "integer", "description", "Max traces (default 20)", "required", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        Object service = arguments.get("service");
        if (service == null || service.toString().isBlank()) {
            return ToolResult.fail("service is required");
        }
        int minutesBack = intArg(arguments.get("minutesBack"), 30);
        int limit = intArg(arguments.get("limit"), 20);
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(minutesBack));

        try {
            // Tempo search API (tag-based). Falls back gracefully if unavailable.
            String uri = UriComponentsBuilder.fromPath("/api/search")
                .queryParam("tags", "service.name=" + service)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("limit", limit)
                .build()
                .toUriString();

            JsonNode body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(15));

            List<Map<String, Object>> traces = new ArrayList<>();
            if (body != null && body.path("traces").isArray()) {
                for (JsonNode t : body.path("traces")) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("traceID", t.path("traceID").asText());
                    row.put("rootServiceName", t.path("rootServiceName").asText(null));
                    row.put("rootTraceName", t.path("rootTraceName").asText(null));
                    row.put("durationMs", t.path("durationMs").asLong(0));
                    traces.add(row);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("service", service.toString());
            data.put("traceCount", traces.size());
            data.put("traces", traces);

            return ToolResult.ok("Tempo returned " + traces.size() + " traces for " + service, data);
        } catch (Exception ex) {
            log.warn("tempo_search failed: {}", ex.getMessage());
            return ToolResult.fail("Tempo search error: " + ex.getMessage());
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
