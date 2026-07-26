package com.bankingplatform.aiagent.ollama;

import com.bankingplatform.aiagent.config.AiAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for local Ollama (native /api/chat and /api/embeddings).
 * Tuned for 1B–8B models on laptop GPUs/NPUs (Mac Apple Silicon).
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final AiAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OllamaClient(AiAgentProperties properties, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String base = trimSlash(properties.getOllama().getBaseUrl());
        this.webClient = builder.baseUrl(base).build();
    }

    public boolean isReachable() {
        try {
            JsonNode tags = webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));
            return tags != null;
        } catch (Exception ex) {
            log.debug("Ollama not reachable: {}", ex.getMessage());
            return false;
        }
    }

    public List<String> listModels() {
        List<String> models = new ArrayList<>();
        try {
            JsonNode tags = webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));
            if (tags != null && tags.path("models").isArray()) {
                for (JsonNode m : tags.path("models")) {
                    String name = m.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        models.add(name);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to list Ollama models: {}", ex.getMessage());
        }
        return models;
    }

    public String chat(String systemPrompt, String userPrompt) {
        AiAgentProperties.Ollama cfg = properties.getOllama();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", cfg.getChatModel());
            payload.put("stream", false);
            ObjectNode options = payload.putObject("options");
            options.put("temperature", cfg.getTemperature());
            // Keep context small so Docker Desktop Mac does not OOM-kill llama-server.
            options.put("num_ctx", Math.max(512, cfg.getNumCtx()));
            options.put("num_predict", Math.max(64, cfg.getNumPredict()));

            ArrayNode messages = payload.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }
            messages.addObject().put("role", "user").put("content", userPrompt);

            JsonNode response = webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(cfg.getTimeoutSeconds()));

            if (response == null) {
                throw new IllegalStateException("Empty Ollama chat response");
            }
            String content = response.path("message").path("content").asText("");
            if (content.isBlank()) {
                content = response.path("response").asText("");
            }
            return content;
        } catch (WebClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Ollama chat failed: {} body={}", ex.getMessage(), truncate(body, 500));
            throw new IllegalStateException(friendlyOllamaError(ex.getStatusCode().value(), body), ex);
        } catch (Exception ex) {
            log.warn("Ollama chat failed: {}", ex.getMessage());
            throw new IllegalStateException("Ollama chat failed: " + ex.getMessage(), ex);
        }
    }

    public float[] embed(String text) {
        AiAgentProperties.Ollama cfg = properties.getOllama();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", cfg.getEmbedModel());
            payload.put("prompt", text == null ? "" : text);

            JsonNode response = webClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(Math.min(60, cfg.getTimeoutSeconds())));

            if (response == null || !response.path("embedding").isArray()) {
                throw new IllegalStateException("Empty Ollama embedding response");
            }
            JsonNode arr = response.path("embedding");
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vector[i] = (float) arr.get(i).asDouble();
            }
            return vector;
        } catch (Exception ex) {
            log.warn("Ollama embed failed: {}", ex.getMessage());
            throw new IllegalStateException("Ollama embed failed: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseUrl", properties.getOllama().getBaseUrl());
        out.put("chatModel", properties.getOllama().getChatModel());
        out.put("embedModel", properties.getOllama().getEmbedModel());
        boolean reachable = isReachable();
        out.put("reachable", reachable);
        out.put("models", reachable ? listModels() : List.of());
        return out;
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:11434";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String friendlyOllamaError(int status, String body) {
        String lower = body == null ? "" : body.toLowerCase();
        if (lower.contains("signal: killed") || lower.contains("out of memory") || lower.contains("oom")) {
            return "Ollama ran out of memory loading the chat model (Docker likely killed llama-server). "
                + "Use a smaller model (llama3.2:1b) or raise Docker Desktop memory to ≥8GB. "
                + "Details: " + truncate(body, 240);
        }
        return "Ollama chat failed (" + status + "): " + truncate(body, 240);
    }
}
