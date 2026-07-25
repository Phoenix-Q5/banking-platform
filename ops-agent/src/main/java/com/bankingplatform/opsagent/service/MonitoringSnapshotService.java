package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds a live monitoring snapshot by driving the same observability tools the
 * LLM uses (Prometheus, Tempo, Loki). Powers the ops-agent Command Center POC.
 */
@Service
public class MonitoringSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSnapshotService.class);

    static final String JOB_REGEX =
        "api-gateway|account-service|transaction-service|customer-service|payment-service|"
            + "card-service|notification-service|audit-service|loan-service|ops-agent";

    private static final List<String> TRACE_SERVICES = List.of(
        "api-gateway", "transaction-service", "account-service", "payment-service");

    private final ToolRegistry toolRegistry;
    private final OpsAgentProperties properties;

    public MonitoringSnapshotService(ToolRegistry toolRegistry, OpsAgentProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public Map<String, Object> snapshot() {
        Instant generatedAt = Instant.now();

        Map<String, Double> upByJob = vectorByLabel(prom(
            "up{job=~\"" + JOB_REGEX + "\"}"), "job");
        Map<String, Double> errorRateByJob = vectorByLabel(prom(
            "sum by (job) (rate(http_server_requests_seconds_count{job=~\"" + JOB_REGEX + "\",status=~\"5..\"}[2m]))"
                + " / clamp_min(sum by (job) (rate(http_server_requests_seconds_count{job=~\"" + JOB_REGEX + "\"}[2m])), 0.001) * 100"),
            "job");
        Map<String, Double> p95ByJob = vectorByLabel(prom(
            "histogram_quantile(0.95, sum by (job, le) (rate(http_server_requests_seconds_bucket{job=~\""
                + JOB_REGEX + "\"}[5m]))) * 1000"), "job");
        Map<String, Double> rpsByJob = vectorByLabel(prom(
            "sum by (job) (rate(http_server_requests_seconds_count{job=~\"" + JOB_REGEX + "\"}[2m]))"), "job");
        Map<String, Double> heapUsedByJob = vectorByLabel(prom(
            "sum by (job) (jvm_memory_used_bytes{job=~\"" + JOB_REGEX + "\",area=\"heap\"})"), "job");
        Map<String, Double> heapMaxByJob = vectorByLabel(prom(
            "sum by (job) (jvm_memory_max_bytes{job=~\"" + JOB_REGEX + "\",area=\"heap\"})"), "job");
        Map<String, Double> gcPauseByJob = vectorByLabel(prom(
            "sum by (job) (rate(jvm_gc_pause_seconds_sum{job=~\"" + JOB_REGEX + "\"}[1m]))"), "job");
        Map<String, Double> cpuByJob = vectorByLabel(prom(
            "process_cpu_usage{job=~\"" + JOB_REGEX + "\"}"), "job");
        Map<String, Double> threadsByJob = vectorByLabel(prom(
            "jvm_threads_live_threads{job=~\"" + JOB_REGEX + "\"}"), "job");

        List<Map<String, Object>> services = buildServices(
            upByJob, errorRateByJob, p95ByJob, rpsByJob,
            heapUsedByJob, heapMaxByJob, gcPauseByJob, cpuByJob, threadsByJob);

        List<Map<String, Object>> slowestEndpoints = buildSlowestEndpoints();
        List<Map<String, Object>> alerts = buildAlerts();
        List<Map<String, Object>> traces = buildSlowTraces();
        List<Map<String, Object>> recentErrors = buildRecentErrors();
        List<Map<String, Object>> signals = buildSignals(services, slowestEndpoints, alerts, traces);
        String overall = overallStatus(signals, services);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", generatedAt.toString());
        out.put("platform", properties.getPlatformName());
        out.put("overallStatus", overall);
        out.put("sources", Map.of(
            "prometheus", properties.getObservability().getPrometheusUrl(),
            "loki", properties.getObservability().getLokiUrl(),
            "tempo", properties.getObservability().getTempoUrl()
        ));
        out.put("summary", Map.of(
            "servicesUp", services.stream().filter(s -> Boolean.TRUE.equals(s.get("up"))).count(),
            "servicesTotal", services.size(),
            "firingAlerts", alerts.size(),
            "warningSignals", signals.stream().filter(s -> "WARNING".equals(s.get("severity"))).count(),
            "criticalSignals", signals.stream().filter(s -> "CRITICAL".equals(s.get("severity"))).count(),
            "slowEndpoints", slowestEndpoints.size(),
            "slowTraces", traces.size()
        ));
        out.put("signals", signals);
        out.put("services", services);
        out.put("slowestEndpoints", slowestEndpoints);
        out.put("jvm", Map.of(
            "heap", services.stream().map(s -> Map.of(
                "service", s.get("name"),
                "heapUsedBytes", s.getOrDefault("heapUsedBytes", 0),
                "heapMaxBytes", s.getOrDefault("heapMaxBytes", 0),
                "heapUsedRatio", s.getOrDefault("heapUsedRatio", 0)
            )).toList(),
            "gc", services.stream().map(s -> Map.of(
                "service", s.get("name"),
                "gcPauseSecondsPerSec", s.getOrDefault("gcPauseSecondsPerSec", 0)
            )).toList()
        ));
        out.put("traces", traces);
        out.put("alerts", alerts);
        out.put("recentErrors", recentErrors);
        out.put("suggestedPrompts", suggestedPrompts(signals, slowestEndpoints, services));
        out.put("capabilities", List.of(
            "prometheus_query — RED metrics, JVM, circuit breakers",
            "loki_query — ERROR stacks and failure context",
            "tempo_search — slow / error traces",
            "service_health — Actuator health probes",
            "circuit_breaker_state — Resilience4j state"
        ));
        return out;
    }

    /**
     * Builds an investigation prompt that embeds the live snapshot so the LLM
     * (or heuristic engine) can reason with concrete telemetry.
     */
    public String buildExplainPrompt(String focus, String operatorMessage, Map<String, Object> snap) {
        String area = focus == null || focus.isBlank() ? "overview" : focus.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("Command Center monitoring explain request (focus=").append(area).append(").\n");
        sb.append("Use prometheus_query, loki_query, tempo_search, and service_health as needed.\n");
        sb.append("Overall status: ").append(snap.get("overallStatus")).append(".\n");
        sb.append("Summary: ").append(snap.get("summary")).append(".\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) snap.getOrDefault("signals", List.of());
        if (!signals.isEmpty()) {
            sb.append("Active signals:\n");
            for (Map<String, Object> signal : signals) {
                sb.append("- [").append(signal.get("severity")).append("] ")
                    .append(signal.get("title")).append(": ")
                    .append(signal.get("detail")).append('\n');
            }
        }

        switch (area) {
            case "endpoints" -> {
                sb.append("Focus on endpoint latency breakdown and identify the slowest APIs.\n");
                sb.append("Slowest endpoints snapshot: ").append(snap.get("slowestEndpoints")).append('\n');
            }
            case "heap", "gc", "jvm" -> {
                sb.append("Focus on JVM heap pressure and GC pause rates across services.\n");
                sb.append("JVM snapshot: ").append(snap.get("jvm")).append('\n');
            }
            case "traces" -> {
                sb.append("Focus on slow traces from Tempo and correlate with latency metrics.\n");
                sb.append("Recent slow traces: ").append(snap.get("traces")).append('\n');
            }
            case "alerts" -> {
                sb.append("Focus on firing Prometheus alerts and recommended mitigations.\n");
                sb.append("Alerts: ").append(snap.get("alerts")).append('\n');
            }
            case "health" -> {
                sb.append("Focus on service health / up targets and unreachable dependencies.\n");
                sb.append("Services: ").append(snap.get("services")).append('\n');
            }
            default -> sb.append("Give a concise platform health briefing with top risks and next checks.\n");
        }

        if (operatorMessage != null && !operatorMessage.isBlank()) {
            sb.append("Operator question: ").append(operatorMessage.trim()).append('\n');
        }
        return sb.toString();
    }

    private List<Map<String, Object>> buildServices(
            Map<String, Double> up,
            Map<String, Double> errorRate,
            Map<String, Double> p95,
            Map<String, Double> rps,
            Map<String, Double> heapUsed,
            Map<String, Double> heapMax,
            Map<String, Double> gcPause,
            Map<String, Double> cpu,
            Map<String, Double> threads) {

        TreeMap<String, Map<String, Object>> byName = new TreeMap<>();
        for (String job : JOB_REGEX.split("\\|")) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", job);
            boolean isUp = up.getOrDefault(job, 0d) >= 1.0;
            row.put("up", isUp);
            row.put("status", isUp ? "UP" : "DOWN");
            row.put("errorRatePct", round(errorRate.getOrDefault(job, 0d), 2));
            row.put("p95Ms", round(p95.getOrDefault(job, 0d), 1));
            row.put("rps", round(rps.getOrDefault(job, 0d), 2));
            double used = heapUsed.getOrDefault(job, 0d);
            double max = heapMax.getOrDefault(job, 0d);
            row.put("heapUsedBytes", Math.round(used));
            row.put("heapMaxBytes", Math.round(max));
            row.put("heapUsedRatio", max > 0 ? round(used / max, 3) : 0);
            row.put("gcPauseSecondsPerSec", round(gcPause.getOrDefault(job, 0d), 4));
            row.put("cpuUsage", round(cpu.getOrDefault(job, 0d), 3));
            row.put("liveThreads", (int) Math.round(threads.getOrDefault(job, 0d)));
            byName.put(job, row);
        }
        // Include any unexpected jobs Prometheus returned
        for (String job : up.keySet()) {
            byName.computeIfAbsent(job, j -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", j);
                row.put("up", up.getOrDefault(j, 0d) >= 1.0);
                row.put("status", up.getOrDefault(j, 0d) >= 1.0 ? "UP" : "DOWN");
                return row;
            });
        }
        return new ArrayList<>(byName.values());
    }

    private List<Map<String, Object>> buildSlowestEndpoints() {
        // Average latency by method+uri (more stable than sparse histograms for POC)
        ToolResult avg = prom(
            "topk(12, sum by (job, method, uri) (rate(http_server_requests_seconds_sum{job=~\"" + JOB_REGEX
                + "\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\"}[5m]))"
                + " / clamp_min(sum by (job, method, uri) (rate(http_server_requests_seconds_count{job=~\""
                + JOB_REGEX + "\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\"}[5m])), 0.001) * 1000)");
        ToolResult rate = prom(
            "sum by (job, method, uri) (rate(http_server_requests_seconds_count{job=~\"" + JOB_REGEX
                + "\",uri!=\"/actuator/prometheus\",uri!=\"/actuator/health\"}[5m]))");

        Map<String, Double> rpsIndex = new LinkedHashMap<>();
        for (JsonNode sample : resultArray(rate)) {
            JsonNode metric = sample.path("metric");
            String key = metricKey(metric, "job", "method", "uri");
            rpsIndex.put(key, valueOf(sample));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode sample : resultArray(avg)) {
            JsonNode metric = sample.path("metric");
            String job = text(metric, "job");
            String method = text(metric, "method");
            String uri = text(metric, "uri");
            double latencyMs = valueOf(sample);
            if (uri.isBlank() || latencyMs <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("job", job);
            row.put("method", method);
            row.put("uri", uri);
            row.put("avgLatencyMs", round(latencyMs, 1));
            row.put("rps", round(rpsIndex.getOrDefault(metricKey(metric, "job", "method", "uri"), 0d), 3));
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(r -> -((Number) r.get("avgLatencyMs")).doubleValue()));
        return rows;
    }

    private List<Map<String, Object>> buildAlerts() {
        ToolResult result = prom("ALERTS{alertstate=\"firing\"}");
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (JsonNode sample : resultArray(result)) {
            JsonNode metric = sample.path("metric");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("alertname", text(metric, "alertname"));
            row.put("severity", text(metric, "severity").isBlank() ? "warning" : text(metric, "severity"));
            row.put("job", text(metric, "job"));
            row.put("service", text(metric, "service"));
            row.put("state", text(metric, "alertstate"));
            row.put("summary", text(metric, "summary").isBlank() ? text(metric, "alertname") : text(metric, "summary"));
            alerts.add(row);
        }
        return alerts;
    }

    private List<Map<String, Object>> buildSlowTraces() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (String service : TRACE_SERVICES) {
            ToolResult result = toolRegistry.invoke("tempo_search", Map.of(
                "service", service,
                "minutesBack", 30,
                "limit", 8
            ));
            if (!result.isSuccess() || !(result.getData() instanceof Map<?, ?> data)) {
                continue;
            }
            Object tracesObj = data.get("traces");
            if (!(tracesObj instanceof List<?> traces)) {
                continue;
            }
            for (Object t : traces) {
                if (!(t instanceof Map<?, ?> trace)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("service", service);
                Object traceId = trace.get("traceID");
                row.put("traceID", traceId == null ? "" : String.valueOf(traceId));
                row.put("rootServiceName", trace.get("rootServiceName"));
                row.put("rootTraceName", trace.get("rootTraceName"));
                long duration = 0L;
                Object d = trace.get("durationMs");
                if (d instanceof Number n) {
                    duration = n.longValue();
                }
                row.put("durationMs", duration);
                all.add(row);
            }
        }
        all.sort(Comparator.comparingLong(r -> -((Number) r.get("durationMs")).longValue()));
        if (all.size() > 15) {
            return all.subList(0, 15);
        }
        return all;
    }

    private List<Map<String, Object>> buildRecentErrors() {
        // Prefer service label (common in this stack); fall back if Loki rejects the selector.
        ToolResult result = toolRegistry.invoke("loki_query", Map.of(
            "query", "{service=~\"" + JOB_REGEX + "\"} |= \"ERROR\"",
            "limit", 12,
            "minutesBack", 15
        ));
        if (!result.isSuccess()) {
            result = toolRegistry.invoke("loki_query", Map.of(
                "query", "{job=~\"" + JOB_REGEX + "\"} |= \"ERROR\"",
                "limit", 12,
                "minutesBack", 15
            ));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!result.isSuccess() || !(result.getData() instanceof Map<?, ?> data)) {
            log.debug("loki recent errors unavailable: {}", result.getSummary());
            return rows;
        }
        Object linesObj = data.get("lines");
        if (!(linesObj instanceof List<?> lines)) {
            return rows;
        }
        for (Object line : lines) {
            if (line == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("line", truncate(line.toString(), 240));
            rows.add(row);
            if (rows.size() >= 12) {
                break;
            }
        }
        return rows;
    }

    private List<Map<String, Object>> buildSignals(
            List<Map<String, Object>> services,
            List<Map<String, Object>> endpoints,
            List<Map<String, Object>> alerts,
            List<Map<String, Object>> traces) {

        List<Map<String, Object>> signals = new ArrayList<>();
        for (Map<String, Object> svc : services) {
            String name = String.valueOf(svc.get("name"));
            if (!Boolean.TRUE.equals(svc.get("up"))) {
                signals.add(signal("CRITICAL", "Service down", name + " is not scraping / unreachable",
                    "Investigate why " + name + " is down and check recent logs and health"));
            }
            double err = ((Number) svc.getOrDefault("errorRatePct", 0)).doubleValue();
            if (err >= 5.0) {
                signals.add(signal("CRITICAL", "High error rate",
                    name + " 5xx rate is " + err + "%",
                    "Why is " + name + " returning elevated 5xx errors?"));
            } else if (err >= 1.0) {
                signals.add(signal("WARNING", "Elevated error rate",
                    name + " 5xx rate is " + err + "%",
                    "Investigate elevated errors on " + name));
            }
            double p95 = ((Number) svc.getOrDefault("p95Ms", 0)).doubleValue();
            if (p95 >= 1000) {
                signals.add(signal("WARNING", "High latency",
                    name + " p95 is " + p95 + " ms",
                    "Which endpoints on " + name + " are slow and what do Tempo traces show?"));
            }
            double heap = ((Number) svc.getOrDefault("heapUsedRatio", 0)).doubleValue();
            if (heap >= 0.85) {
                signals.add(signal("CRITICAL", "Heap pressure",
                    name + " heap at " + Math.round(heap * 100) + "%",
                    "Analyze JVM heap and GC for " + name));
            } else if (heap >= 0.70) {
                signals.add(signal("WARNING", "Heap elevated",
                    name + " heap at " + Math.round(heap * 100) + "%",
                    "Check heap and GC trends for " + name));
            }
            double gc = ((Number) svc.getOrDefault("gcPauseSecondsPerSec", 0)).doubleValue();
            if (gc >= 0.05) {
                signals.add(signal("WARNING", "GC thrashing",
                    name + " GC pause rate " + gc + " s/s",
                    "Investigate GC pauses on " + name));
            }
        }

        if (!endpoints.isEmpty()) {
            Map<String, Object> top = endpoints.get(0);
            double lat = ((Number) top.getOrDefault("avgLatencyMs", 0)).doubleValue();
            if (lat >= 500) {
                signals.add(signal("WARNING", "Slow endpoint",
                    top.get("method") + " " + top.get("uri") + " on " + top.get("job")
                        + " averages " + lat + " ms",
                    "Explain why " + top.get("method") + " " + top.get("uri")
                        + " on " + top.get("job") + " is slow using metrics and traces"));
            }
        }

        for (Map<String, Object> alert : alerts) {
            String sev = String.valueOf(alert.getOrDefault("severity", "warning")).toUpperCase(Locale.ROOT);
            if (!"CRITICAL".equals(sev) && !"WARNING".equals(sev)) {
                sev = "WARNING";
            }
            signals.add(signal(sev, "Alert: " + alert.get("alertname"),
                String.valueOf(alert.get("summary")),
                "Investigate firing alert " + alert.get("alertname")));
        }

        for (Map<String, Object> trace : traces) {
            long duration = ((Number) trace.getOrDefault("durationMs", 0)).longValue();
            if (duration >= 2000) {
                signals.add(signal("WARNING", "Slow trace",
                    trace.get("service") + " trace " + trace.get("traceID") + " took " + duration + " ms",
                    "Analyze slow Tempo trace " + trace.get("traceID") + " for " + trace.get("service")));
                break; // one representative slow-trace signal is enough
            }
        }

        signals.sort(Comparator.comparingInt(s -> "CRITICAL".equals(s.get("severity")) ? 0 : 1));
        return signals;
    }

    private List<String> suggestedPrompts(
            List<Map<String, Object>> signals,
            List<Map<String, Object>> endpoints,
            List<Map<String, Object>> services) {
        List<String> prompts = new ArrayList<>();
        for (Map<String, Object> signal : signals) {
            Object p = signal.get("suggestPrompt");
            if (p != null && prompts.size() < 4) {
                prompts.add(String.valueOf(p));
            }
        }
        if (prompts.size() < 5 && !endpoints.isEmpty()) {
            Map<String, Object> top = endpoints.get(0);
            prompts.add("Which endpoint is taking the longest and what is the likely bottleneck?");
            prompts.add("Break down latency for " + top.get("job") + " " + top.get("method") + " " + top.get("uri"));
        }
        prompts.add("Give me a platform health briefing covering heap, GC, errors, and slow traces");
        long down = services.stream().filter(s -> !Boolean.TRUE.equals(s.get("up"))).count();
        if (down > 0) {
            prompts.add("Which services are down and what should we check first?");
        }
        return prompts.stream().distinct().limit(6).toList();
    }

    private String overallStatus(List<Map<String, Object>> signals, List<Map<String, Object>> services) {
        boolean anyDown = services.stream().anyMatch(s -> !Boolean.TRUE.equals(s.get("up")));
        boolean critical = signals.stream().anyMatch(s -> "CRITICAL".equals(s.get("severity")));
        boolean warning = signals.stream().anyMatch(s -> "WARNING".equals(s.get("severity")));
        if (anyDown || critical) {
            return "CRITICAL";
        }
        if (warning) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }

    private Map<String, Object> signal(String severity, String title, String detail, String suggestPrompt) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("severity", severity);
        s.put("title", title);
        s.put("detail", detail);
        s.put("suggestPrompt", suggestPrompt);
        return s;
    }

    private ToolResult prom(String query) {
        return toolRegistry.invoke("prometheus_query", Map.of("query", query));
    }

    private Map<String, Double> vectorByLabel(ToolResult result, String label) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (JsonNode sample : resultArray(result)) {
            String key = text(sample.path("metric"), label);
            if (!key.isBlank()) {
                out.put(key, valueOf(sample));
            }
        }
        return out;
    }

    private List<JsonNode> resultArray(ToolResult result) {
        List<JsonNode> out = new ArrayList<>();
        if (!result.isSuccess() || !(result.getData() instanceof Map<?, ?> data)) {
            return out;
        }
        Object raw = data.get("result");
        if (raw instanceof JsonNode node && node.isArray()) {
            node.forEach(out::add);
        } else if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof JsonNode n) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    private double valueOf(JsonNode sample) {
        JsonNode value = sample.path("value");
        if (value.isArray() && value.size() >= 2) {
            try {
                return Double.parseDouble(value.get(1).asText("0"));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private String text(JsonNode metric, String field) {
        return metric.path(field).asText("");
    }

    private String metricKey(JsonNode metric, String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(text(metric, f));
        }
        return sb.toString();
    }

    private double round(double v, int places) {
        double factor = Math.pow(10, places);
        return Math.round(v * factor) / factor;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
