package com.bankingplatform.aiagent.config;

import com.bankingplatform.aiagent.ollama.OllamaClient;
import com.bankingplatform.aiagent.rag.InMemoryVectorStore;
import com.bankingplatform.aiagent.rag.RagIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagStartupRunner.class);

    private final AiAgentProperties properties;
    private final InMemoryVectorStore store;
    private final RagIndexer indexer;
    private final OllamaClient ollamaClient;

    public RagStartupRunner(AiAgentProperties properties,
                            InMemoryVectorStore store,
                            RagIndexer indexer,
                            OllamaClient ollamaClient) {
        this.properties = properties;
        this.store = store;
        this.indexer = indexer;
        this.ollamaClient = ollamaClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean loaded = store.loadIfPresent();
        if (loaded) {
            log.info("Using persisted RAG index ({} chunks)", store.size());
            return;
        }
        if (!properties.getRag().isAutoIndexOnStartup()) {
            log.info("RAG auto-index disabled; call POST /api/ai/index when ready");
            return;
        }
        if (!ollamaClient.isReachable()) {
            log.warn("Ollama not reachable at {} — skip auto-index. Start Ollama, pull models, then POST /api/ai/index",
                properties.getOllama().getBaseUrl());
            return;
        }
        log.info("Building RAG index on startup (this may take a few minutes on first run)…");
        var result = indexer.indexRepository();
        log.info("Startup index result: {}", result);
    }
}
