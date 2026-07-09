package com.bankingplatform.opsagent.llm;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic investigation planner used when no external LLM is configured.
 * Encodes SRE playbook knowledge for banking microservice failures so the agent
 * is useful out-of-the-box and as a fallback when the LLM is unavailable.
 */
@Component
public class HeuristicReasoningEngine implements ReasoningEngine {

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public LlmDecision next(Incident incident, List<Map<String, Object>> toolCatalog, List<ToolResult> priorResults, int round) {
        String service = resolveService(incident);
        String category = incident.getCategory() == null ? "" : incident.getCategory().toLowerCase(Locale.ROOT);
        String title = (incident.getTitle() == null ? "" : incident.getTitle()).toLowerCase(Locale.ROOT);
        String blob = (title + " " + nullSafe(incident.getSummary()) + " " + category).toLowerCase(Locale.ROOT);

        if (round == 0) {
            return LlmDecision.toolCall("service_health", Map.of("service", service));
        }
        if (round == 1) {
            return LlmDecision.toolCall("prometheus_query", Map.of(
                "query", "up{job=\"" + service + "\"} or up{service=\"" + service + "\"}"
            ));
        }
        if (round == 2) {
            if (blob.contains("circuit") || blob.contains("resiliency") || "transaction-service".equals(service)) {
                return LlmDecision.toolCall("circuit_breaker_state", Map.of("service",
                    "transaction-service".equals(service) || "api-gateway".equals(service) ? service : "transaction-service"));
            }
            return LlmDecision.toolCall("prometheus_query", Map.of(
                "query",
                "sum(rate(http_server_requests_seconds_count{application=\"" + service + "\",status=~\"5..\"}[5m])) "
                    + "/ clamp_min(sum(rate(http_server_requests_seconds_count{application=\"" + service + "\"}[5m])),0.001)"
            ));
        }
        if (round == 3) {
            return LlmDecision.toolCall("loki_query", Map.of(
                "query", "{service=\"" + service + "\"} |= \"ERROR\" or {service=\"" + service + "\"} |= \"error\"",
                "limit", 30,
                "minutesBack", 20
            ));
        }
        if (round == 4) {
            return LlmDecision.toolCall("tempo_search", Map.of("service", service, "minutesBack", 30, "limit", 10));
        }

        return buildFinal(incident, service, priorResults, blob);
    }

    private LlmDecision buildFinal(Incident incident, String service, List<ToolResult> priorResults, String blob) {
        List<String> findings = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<String> resiliency = new ArrayList<>();
        String hypothesis;
        String playbook = "generic-degradation";

        boolean unreachable = priorResults.stream().anyMatch(r ->
            r.getSummary() != null && r.getSummary().toLowerCase(Locale.ROOT).contains("unreachable"));
        boolean down = priorResults.stream().anyMatch(r ->
            r.getSummary() != null && r.getSummary().toLowerCase(Locale.ROOT).contains("status=down"));
        boolean cbOpen = priorResults.stream().anyMatch(r ->
            String.valueOf(r.getData()).toLowerCase(Locale.ROOT).contains("\"state\":\"open\"")
                || String.valueOf(r.getData()).toLowerCase(Locale.ROOT).contains("open"));

        if (unreachable || down || blob.contains("service down") || blob.contains("availability")) {
            hypothesis = service + " appears unavailable or reporting DOWN health. Likely process crash, OOM, failed deploy, or dependency (DB) outage.";
            playbook = "service-down";
            findings.add(service + " health/reachability checks indicate an availability problem.");
            findings.add("Downstream callers (gateway / transaction-service) will degrade via circuit breakers and fallbacks.");
            actions.add("Verify container/pod status and recent deploy history for " + service + ".");
            actions.add("Inspect /actuator/health details and Postgres connectivity if DB-backed.");
            actions.add("Confirm api-gateway fallbacks return DEGRADED instead of hanging.");
            actions.add("Page on-call and open a war-room channel; freeze non-critical releases.");
            resiliency.add("Circuit breakers should open after repeated failures — confirm OPEN state on callers.");
            resiliency.add("Prefer fail-fast + retry with backoff over cascading timeouts.");
        } else if (cbOpen || blob.contains("circuit")) {
            hypothesis = "Circuit breaker is OPEN for a downstream dependency of " + service + ". Calls are failing fast to protect the platform.";
            playbook = "circuit-breaker-open";
            findings.add("Circuit breaker state indicates OPEN (or recent open transitions).");
            findings.add("Upstream transfer/API traffic is likely being rejected with DEGRADED responses.");
            actions.add("Identify the failing downstream (usually account-service from transaction-service).");
            actions.add("Check downstream health, error rate, and latency before forcing a reset.");
            actions.add("If downstream is healthy again, wait for half-open probes or carefully reset breaker.");
            resiliency.add("Do not disable the circuit breaker under load — it is protecting the platform.");
            resiliency.add("Validate retry budgets and timeout settings after recovery.");
        } else if (blob.contains("latency") || blob.contains("slow")) {
            hypothesis = "Elevated latency on " + service + ". Possible DB contention, GC pressure, or slow downstream calls.";
            playbook = "high-latency";
            findings.add("Latency-oriented alert or symptoms reported for " + service + ".");
            actions.add("Inspect Tempo traces for slow spans and DB/query hotspots.");
            actions.add("Check Hikari pool saturation and Postgres locks.");
            actions.add("Scale read replicas / increase pool size only after confirming root cause.");
            resiliency.add("TimeLimiter + circuit breaker should bound blast radius of slow calls.");
        } else if (blob.contains("error") || blob.contains("5xx")) {
            hypothesis = "Elevated error rate on " + service + ". Application exceptions or dependency failures are likely.";
            playbook = "high-error-rate";
            findings.add("Error-rate signal detected for " + service + ".");
            actions.add("Pull recent ERROR logs from Loki and group by exception type.");
            actions.add("Correlate failing endpoints with Tempo error traces.");
            actions.add("Roll back recent change if error onset matches a deploy.");
            resiliency.add("Ensure gateway returns structured DEGRADED responses during partial outages.");
        } else {
            hypothesis = "Degraded behavior detected around " + service + ". Evidence gathered from health, metrics, logs, and traces.";
            findings.add("Completed multi-signal investigation across health, Prometheus, Loki, and Tempo.");
            actions.add("Review collected evidence and escalate if customer impact is confirmed.");
            actions.add("Continue monitoring for 15 minutes after any mitigation.");
            resiliency.add("Keep Alertmanager → ops-agent webhook path healthy for auto-investigation.");
        }

        for (ToolResult r : priorResults) {
            if (r.getSummary() != null && !r.getSummary().isBlank()) {
                findings.add("Tool: " + r.getSummary());
            }
        }

        LlmDecision decision = LlmDecision.finale(buildMarkdown(incident, service, hypothesis, findings, actions, resiliency));
        decision.setRootCauseHypothesis(hypothesis);
        decision.setFindings(findings);
        decision.setRecommendedActions(actions);
        decision.setResiliencyNotes(resiliency);
        decision.setPlaybookHint(playbook);
        return decision;
    }

    private String buildMarkdown(Incident incident, String service, String hypothesis,
                                 List<String> findings, List<String> actions, List<String> resiliency) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Incident Report: ").append(nullSafe(incident.getTitle())).append("\n\n");
        sb.append("- **Incident ID**: ").append(incident.getId()).append("\n");
        sb.append("- **Severity**: ").append(incident.getSeverity()).append("\n");
        sb.append("- **Affected service**: ").append(service).append("\n");
        sb.append("- **Category**: ").append(nullSafe(incident.getCategory())).append("\n\n");
        sb.append("## Executive summary\n").append(hypothesis).append("\n\n");
        sb.append("## Findings\n");
        for (String f : findings) {
            sb.append("- ").append(f).append("\n");
        }
        sb.append("\n## Recommended mitigation\n");
        for (String a : actions) {
            sb.append("- ").append(a).append("\n");
        }
        sb.append("\n## Resiliency notes\n");
        for (String r : resiliency) {
            sb.append("- ").append(r).append("\n");
        }
        sb.append("\n---\n_Generated by banking-platform ops-agent (heuristic engine)._\n");
        return sb.toString();
    }

    private String resolveService(Incident incident) {
        if (incident.getAffectedService() != null && !incident.getAffectedService().isBlank()) {
            return incident.getAffectedService();
        }
        String blob = (nullSafe(incident.getTitle()) + " " + nullSafe(incident.getSummary())).toLowerCase(Locale.ROOT);
        if (blob.contains("account")) {
            return "account-service";
        }
        if (blob.contains("gateway") || blob.contains("api-gateway")) {
            return "api-gateway";
        }
        return "transaction-service";
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
