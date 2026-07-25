package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.tools.AgentTool;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringSnapshotServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void snapshotMarksDownServiceAndSurfacesSlowEndpoint() {
        ToolRegistry registry = new ToolRegistry(List.of(stubPrometheus(), stubTempo(), stubLoki()));
        OpsAgentProperties props = new OpsAgentProperties();
        props.setPlatformName("harbor-bank");
        MonitoringSnapshotService service = new MonitoringSnapshotService(registry, props);

        Map<String, Object> snap = service.snapshot();

        assertEquals("harbor-bank", snap.get("platform"));
        assertEquals("CRITICAL", snap.get("overallStatus"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) snap.get("slowestEndpoints");
        assertEquals(1, endpoints.size());
        assertEquals("/api/transfers", endpoints.get(0).get("uri"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) snap.get("signals");
        assertTrue(signals.stream().anyMatch(s -> "CRITICAL".equals(s.get("severity"))));
    }

    @Test
    void buildExplainPromptEmbedsFocusAndOperatorMessage() {
        ToolRegistry registry = new ToolRegistry(List.of());
        MonitoringSnapshotService service = new MonitoringSnapshotService(registry, new OpsAgentProperties());

        Map<String, Object> snap = Map.of(
            "overallStatus", "DEGRADED",
            "summary", Map.of("servicesUp", 9),
            "signals", List.of(Map.of("severity", "WARNING", "title", "High latency", "detail", "p95 high")),
            "slowestEndpoints", List.of(Map.of("uri", "/api/x", "avgLatencyMs", 900)),
            "jvm", Map.of(),
            "traces", List.of(),
            "alerts", List.of(),
            "services", List.of()
        );

        String prompt = service.buildExplainPrompt("endpoints", "Why are transfers slow?", snap);

        assertTrue(prompt.contains("focus=endpoints"));
        assertTrue(prompt.contains("Why are transfers slow?"));
        assertTrue(prompt.contains("DEGRADED"));
        assertTrue(prompt.contains("endpoint latency"));
    }

    private AgentTool stubPrometheus() {
        return new AgentTool() {
            @Override public String name() { return "prometheus_query"; }
            @Override public String description() { return "stub"; }
            @Override public Map<String, Object> parameterSchema() { return Map.of(); }

            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                String query = String.valueOf(arguments.get("query"));
                if (query.startsWith("up{")) {
                    return promResult(Map.of("transaction-service", 0d, "account-service", 1d));
                }
                if (query.contains("status=~\"5..\"")) {
                    return promResult(Map.of("transaction-service", 12d, "account-service", 0d));
                }
                if (query.contains("histogram_quantile(0.95")) {
                    return promResult(Map.of("transaction-service", 1500d, "account-service", 80d));
                }
                if (query.contains("rate(http_server_requests_seconds_count") && query.contains("sum by (job)")) {
                    return promResult(Map.of("transaction-service", 3d, "account-service", 1d));
                }
                if (query.contains("jvm_memory_used_bytes")) {
                    return promResult(Map.of("transaction-service", 900d, "account-service", 100d));
                }
                if (query.contains("jvm_memory_max_bytes")) {
                    return promResult(Map.of("transaction-service", 1000d, "account-service", 1000d));
                }
                if (query.contains("jvm_gc_pause_seconds_sum")) {
                    return promResult(Map.of("transaction-service", 0.08d, "account-service", 0.001d));
                }
                if (query.contains("process_cpu_usage")) {
                    return promResult(Map.of("transaction-service", 0.4d, "account-service", 0.1d));
                }
                if (query.contains("jvm_threads_live_threads")) {
                    return promResult(Map.of("transaction-service", 80d, "account-service", 40d));
                }
                if (query.startsWith("topk(12")) {
                    return endpointLatencyResult();
                }
                if (query.contains("sum by (job, method, uri)")) {
                    return endpointRpsResult();
                }
                if (query.startsWith("ALERTS")) {
                    return ToolResult.ok("0 series", Map.of("resultType", "vector", "result", mapper.createArrayNode()));
                }
                return ToolResult.ok("empty", Map.of("resultType", "vector", "result", mapper.createArrayNode()));
            }
        };
    }

    private AgentTool stubTempo() {
        return new AgentTool() {
            @Override public String name() { return "tempo_search"; }
            @Override public String description() { return "stub"; }
            @Override public Map<String, Object> parameterSchema() { return Map.of(); }
            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                return ToolResult.ok("traces", Map.of("traces", List.of(
                    Map.of("traceID", "abc123", "rootTraceName", "POST /transfers", "durationMs", 3200L)
                )));
            }
        };
    }

    private AgentTool stubLoki() {
        return new AgentTool() {
            @Override public String name() { return "loki_query"; }
            @Override public String description() { return "stub"; }
            @Override public Map<String, Object> parameterSchema() { return Map.of(); }
            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                return ToolResult.ok("logs", Map.of("lines", List.of("ERROR payment failed")));
            }
        };
    }

    private ToolResult promResult(Map<String, Double> byJob) {
        ArrayNode result = mapper.createArrayNode();
        byJob.forEach((job, value) -> {
            ObjectNode sample = result.addObject();
            sample.putObject("metric").put("job", job);
            ArrayNode valueNode = sample.putArray("value");
            valueNode.add(1_700_000_000);
            valueNode.add(String.valueOf(value));
        });
        return ToolResult.ok(byJob.size() + " series", Map.of("resultType", "vector", "result", result));
    }

    private ToolResult endpointLatencyResult() {
        ArrayNode result = mapper.createArrayNode();
        ObjectNode sample = result.addObject();
        ObjectNode metric = sample.putObject("metric");
        metric.put("job", "transaction-service");
        metric.put("method", "POST");
        metric.put("uri", "/api/transfers");
        ArrayNode value = sample.putArray("value");
        value.add(1_700_000_000);
        value.add("850.5");
        return ToolResult.ok("1 series", Map.of("resultType", "vector", "result", result));
    }

    private ToolResult endpointRpsResult() {
        ArrayNode result = mapper.createArrayNode();
        ObjectNode sample = result.addObject();
        ObjectNode metric = sample.putObject("metric");
        metric.put("job", "transaction-service");
        metric.put("method", "POST");
        metric.put("uri", "/api/transfers");
        ArrayNode value = sample.putArray("value");
        value.add(1_700_000_000);
        value.add("2.5");
        return ToolResult.ok("1 series", Map.of("resultType", "vector", "result", result));
    }
}
