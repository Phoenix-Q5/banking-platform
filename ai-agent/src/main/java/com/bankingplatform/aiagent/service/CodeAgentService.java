package com.bankingplatform.aiagent.service;

import com.bankingplatform.aiagent.config.AiAgentProperties;
import com.bankingplatform.aiagent.ollama.OllamaClient;
import com.bankingplatform.aiagent.rag.InMemoryVectorStore;
import com.bankingplatform.aiagent.rag.RagChunk;
import com.bankingplatform.aiagent.rag.RagIndexer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodeAgentService {

    private final AiAgentProperties properties;
    private final OllamaClient ollamaClient;
    private final InMemoryVectorStore store;
    private final RagIndexer indexer;

    public CodeAgentService(AiAgentProperties properties,
                            OllamaClient ollamaClient,
                            InMemoryVectorStore store,
                            RagIndexer indexer) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
        this.store = store;
        this.indexer = indexer;
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platform", properties.getPlatformName());
        out.put("service", "ai-agent");
        out.put("ollama", ollamaClient.status());
        out.put("rag", Map.of(
            "chunks", store.size(),
            "indexing", indexer.isIndexing(),
            "rootPath", properties.getRag().getRootPath(),
            "persistPath", properties.getRag().getPersistPath(),
            "topK", properties.getRag().getTopK()
        ));
        out.put("ready", ollamaClient.isReachable() && store.size() > 0);
        return out;
    }

    public Map<String, Object> reindex() {
        return indexer.indexRepository();
    }

    public Map<String, Object> ask(String question, Integer topKOverride) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        if (!ollamaClient.isReachable()) {
            throw new IllegalStateException("Ollama is not reachable at " + properties.getOllama().getBaseUrl());
        }
        if (store.size() == 0) {
            throw new IllegalStateException("RAG index is empty. Call POST /api/ai/index first.");
        }

        int topK = topKOverride == null ? properties.getRag().getTopK() : Math.max(1, topKOverride);
        float[] queryVec = ollamaClient.embed(question);
        List<InMemoryVectorStore.ScoredChunk> hits = store.search(queryVec, topK);

        StringBuilder context = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            InMemoryVectorStore.ScoredChunk hit = hits.get(i);
            RagChunk chunk = hit.chunk();
            context.append("[").append(i + 1).append("] ")
                .append(chunk.getPath())
                .append(" lines ").append(chunk.getStartLine()).append("-").append(chunk.getEndLine())
                .append(" (score=").append(String.format("%.3f", hit.score())).append(")\n")
                .append(chunk.getText()).append("\n\n");

            Map<String, Object> src = new LinkedHashMap<>();
            src.put("path", chunk.getPath());
            src.put("startLine", chunk.getStartLine());
            src.put("endLine", chunk.getEndLine());
            src.put("score", Math.round(hit.score() * 1000.0) / 1000.0);
            sources.add(src);
        }

        String system = """
            You are Harbor Bank's local code agent running on Ollama.
            Answer using ONLY the retrieved repository context when possible.
            Be concise, concrete, and cite file paths like path:line.
            If context is insufficient, say what is missing.
            Prefer actionable guidance for analysis and fixes; do not invent APIs that are not in context.
            """;

        String user = "Question:\n" + question.trim() + "\n\nRetrieved context:\n" + context;
        String answer = ollamaClient.chat(system, user);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", answer);
        out.put("model", properties.getOllama().getChatModel());
        out.put("embedModel", properties.getOllama().getEmbedModel());
        out.put("sources", sources);
        out.put("chunksUsed", sources.size());
        return out;
    }

    public Map<String, Object> analyze(String focusPathOrTopic) {
        String topic = focusPathOrTopic == null || focusPathOrTopic.isBlank()
            ? "overall architecture and risk hotspots"
            : focusPathOrTopic.trim();
        String question = """
            Perform a code analysis for: %s

            Cover:
            1) What this area does
            2) Key classes / endpoints
            3) Risks, bugs, or missing resilience
            4) Concrete fix suggestions prioritized by impact
            """.formatted(topic);
        return ask(question, Math.max(properties.getRag().getTopK(), 8));
    }
}
