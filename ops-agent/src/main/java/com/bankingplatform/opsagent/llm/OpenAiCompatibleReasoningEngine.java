package com.bankingplatform.opsagent.llm;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.tools.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions client with tool-calling.
 * Works with OpenAI, Azure OpenAI (compatible path), Ollama, and local gateways.
 */
@Component
public class OpenAiCompatibleReasoningEngine implements ReasoningEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleReasoningEngine.class);

    private final OpsAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final HeuristicReasoningEngine fallback;

    public OpenAiCompatibleReasoningEngine(OpsAgentProperties properties,
                                           ObjectMapper objectMapper,
                                           WebClient.Builder webClientBuilder,
                                           HeuristicReasoningEngine fallback) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.fallback = fallback;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    public boolean isAvailable() {
        if (!properties.getLlm().isEnabled()) {
            return false;
        }
        // Ollama and many local gateways do not require a real API key.
        if (isLocalProvider()) {
            return properties.getLlm().getBaseUrl() != null
                && !properties.getLlm().getBaseUrl().isBlank();
        }
        return properties.getLlm().getApiKey() != null
            && !properties.getLlm().getApiKey().isBlank();
    }

    private boolean isLocalProvider() {
        String provider = properties.getLlm().getProvider();
        String base = properties.getLlm().getBaseUrl() == null ? "" : properties.getLlm().getBaseUrl().toLowerCase();
        return "ollama".equalsIgnoreCase(provider)
            || base.contains("11434")
            || base.contains("localhost")
            || base.contains("host.docker.internal");
    }

    @Override
    public LlmDecision next(Incident incident, List<Map<String, Object>> toolCatalog, List<ToolResult> priorResults, int round) {
        if (!isAvailable()) {
            return fallback.next(incident, toolCatalog, priorResults, round);
        }
        try {
            return callModel(incident, toolCatalog, priorResults, round);
        } catch (Exception ex) {
            log.warn("LLM call failed, falling back to heuristic: {}", ex.getMessage());
            return fallback.next(incident, toolCatalog, priorResults, round);
        }
    }

    private LlmDecision callModel(Incident incident, List<Map<String, Object>> toolCatalog,
                                  List<ToolResult> priorResults, int round) throws Exception {
        OpsAgentProperties.Llm llm = properties.getLlm();
        WebClient client = webClientBuilder
            .baseUrl(trimTrailingSlash(llm.getBaseUrl()))
            .defaultHeader("Authorization", "Bearer " + bearerToken(llm))
            .build();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", llm.getModel());
        payload.put("temperature", llm.getTemperature());

        ArrayNode messages = payload.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put("content", systemPrompt());
        messages.addObject()
            .put("role", "user")
            .put("content", userPrompt(incident, priorResults, round));

        ArrayNode tools = payload.putArray("tools");
        for (Map<String, Object> tool : toolCatalog) {
            ObjectNode t = tools.addObject();
            t.put("type", "function");
            ObjectNode fn = t.putObject("function");
            fn.put("name", String.valueOf(tool.get("name")));
            fn.put("description", String.valueOf(tool.get("description")));
            ObjectNode params = fn.putObject("parameters");
            params.put("type", "object");
            ObjectNode properties = params.putObject("properties");
            Object rawParams = tool.get("parameters");
            if (rawParams instanceof Map<?, ?> paramMap) {
                List<String> required = new ArrayList<>();
                for (Map.Entry<?, ?> e : paramMap.entrySet()) {
                    ObjectNode p = properties.putObject(String.valueOf(e.getKey()));
                    if (e.getValue() instanceof Map<?, ?> meta) {
                        Object type = meta.get("type");
                        p.put("type", type == null ? "string" : String.valueOf(type));
                        if (meta.get("description") != null) {
                            p.put("description", String.valueOf(meta.get("description")));
                        }
                        Object requiredFlag = meta.get("required");
                        if (Boolean.TRUE.equals(requiredFlag) || "true".equals(String.valueOf(requiredFlag))) {
                            required.add(String.valueOf(e.getKey()));
                        }
                    }
                }
                ArrayNode req = params.putArray("required");
                required.forEach(req::add);
            }
        }

        JsonNode response = client.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block(Duration.ofSeconds(llm.getTimeoutSeconds()));

        if (response == null) {
            throw new IllegalStateException("Empty LLM response");
        }

        JsonNode message = response.path("choices").path(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            JsonNode call = toolCalls.get(0);
            String name = call.path("function").path("name").asText();
            String argsJson = call.path("function").path("arguments").asText("{}");
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            return LlmDecision.toolCall(name, args);
        }

        String content = message.path("content").asText("");
        return parseFinalContent(content, incident);
    }

    private LlmDecision parseFinalContent(String content, Incident incident) {
        LlmDecision decision = LlmDecision.finale(content);
        decision.setRootCauseHypothesis(extractSection(content, "root cause", "hypothesis"));
        decision.getFindings().addAll(extractBullets(content, "findings"));
        decision.getRecommendedActions().addAll(extractBullets(content, "recommended"));
        decision.getResiliencyNotes().addAll(extractBullets(content, "resiliency"));
        if (decision.getRootCauseHypothesis() == null || decision.getRootCauseHypothesis().isBlank()) {
            decision.setRootCauseHypothesis(content.length() > 280 ? content.substring(0, 280) + "…" : content);
        }
        if (content.toLowerCase().contains("circuit")) {
            decision.setPlaybookHint("circuit-breaker-open");
        } else if (content.toLowerCase().contains("down") || content.toLowerCase().contains("unavailable")) {
            decision.setPlaybookHint("service-down");
        } else if (content.toLowerCase().contains("latency")) {
            decision.setPlaybookHint("high-latency");
        } else if (content.toLowerCase().contains("error")) {
            decision.setPlaybookHint("high-error-rate");
        } else {
            decision.setPlaybookHint("generic-degradation");
        }
        return decision;
    }

    private String systemPrompt() {
        return """
            You are the banking-platform ops agent. You investigate production incidents across
            Spring Boot microservices (api-gateway, account-service, transaction-service) using tools
            that query Prometheus, Loki, Tempo, and Spring Actuator health/circuit-breaker endpoints.

            Goals:
            1. Identify affected service and blast radius
            2. Correlate metrics, logs, traces, and health
            3. Produce a clear root-cause hypothesis
            4. Recommend concrete mitigation and resiliency steps
            5. Prefer fail-safe actions; never suggest disabling auth or deleting data

            When you have enough evidence, respond with a markdown incident report containing sections:
            Executive summary, Findings, Recommended mitigation, Resiliency notes.
            Call at most one tool per turn.
            """;
    }

    private String userPrompt(Incident incident, List<ToolResult> priorResults, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("Platform: ").append(properties.getPlatformName()).append("\n");
        sb.append("Incident: ").append(incident.getTitle()).append("\n");
        sb.append("Severity: ").append(incident.getSeverity()).append("\n");
        sb.append("Service: ").append(incident.getAffectedService()).append("\n");
        sb.append("Category: ").append(incident.getCategory()).append("\n");
        sb.append("Summary: ").append(incident.getSummary()).append("\n");
        sb.append("Round: ").append(round).append("\n\n");
        sb.append("Prior tool results:\n");
        if (priorResults.isEmpty()) {
            sb.append("(none yet)\n");
        } else {
            for (int i = 0; i < priorResults.size(); i++) {
                ToolResult r = priorResults.get(i);
                sb.append(i + 1).append(". success=").append(r.isSuccess())
                    .append(" summary=").append(r.getSummary()).append("\n");
                if (r.getData() != null) {
                    String data = String.valueOf(r.getData());
                    if (data.length() > 1500) {
                        data = data.substring(0, 1500) + "…";
                    }
                    sb.append("   data=").append(data).append("\n");
                }
            }
        }
        sb.append("\nEither call a tool or produce the final markdown report.");
        return sb.toString();
    }

    private String extractSection(String content, String... keywords) {
        String lower = content.toLowerCase();
        for (String kw : keywords) {
            int idx = lower.indexOf(kw);
            if (idx >= 0) {
                int end = content.indexOf('\n', idx);
                if (end < 0) {
                    end = Math.min(content.length(), idx + 200);
                }
                return content.substring(idx, end).replaceAll("(?i)" + kw + "[:\\s]*", "").trim();
            }
        }
        return "";
    }

    private List<String> extractBullets(String content, String headingHint) {
        List<String> bullets = new ArrayList<>();
        String[] lines = content.split("\n");
        boolean inSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().contains(headingHint.toLowerCase()) && trimmed.startsWith("#")) {
                inSection = true;
                continue;
            }
            if (inSection && trimmed.startsWith("#")) {
                break;
            }
            if (inSection && trimmed.startsWith("- ")) {
                bullets.add(trimmed.substring(2).trim());
            }
        }
        return bullets;
    }

    private String bearerToken(OpsAgentProperties.Llm llm) {
        if (llm.getApiKey() != null && !llm.getApiKey().isBlank()) {
            return llm.getApiKey();
        }
        return "ollama";
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
