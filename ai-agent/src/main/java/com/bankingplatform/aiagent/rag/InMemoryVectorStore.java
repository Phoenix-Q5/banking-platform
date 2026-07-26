package com.bankingplatform.aiagent.rag;

import com.bankingplatform.aiagent.config.AiAgentProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final AiAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<RagChunk> chunks = new CopyOnWriteArrayList<>();

    public InMemoryVectorStore(AiAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void replaceAll(List<RagChunk> next) {
        chunks.clear();
        if (next != null) {
            chunks.addAll(next);
        }
    }

    public int size() {
        return chunks.size();
    }

    public List<RagChunk> all() {
        return List.copyOf(chunks);
    }

    public List<ScoredChunk> search(float[] queryEmbedding, int topK) {
        int k = Math.max(1, topK);
        List<ScoredChunk> scored = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            double score = RagChunk.cosine(queryEmbedding, chunk.getEmbedding());
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.size() > k) {
            return scored.subList(0, k);
        }
        return scored;
    }

    public void persist() {
        Path path = Path.of(properties.getRag().getPersistPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), chunks);
            log.info("Persisted {} RAG chunks to {}", chunks.size(), path);
        } catch (IOException ex) {
            log.warn("Failed to persist RAG index: {}", ex.getMessage());
        }
    }

    public boolean loadIfPresent() {
        Path path = Path.of(properties.getRag().getPersistPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            List<RagChunk> loaded = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            replaceAll(loaded);
            log.info("Loaded {} RAG chunks from {}", chunks.size(), path);
            return true;
        } catch (IOException ex) {
            log.warn("Failed to load RAG index: {}", ex.getMessage());
            return false;
        }
    }

    public record ScoredChunk(RagChunk chunk, double score) {
    }
}
