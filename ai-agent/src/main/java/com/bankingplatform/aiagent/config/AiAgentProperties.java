package com.bankingplatform.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "ai-agent")
public class AiAgentProperties {

    private String platformName = "harbor-bank";
    private Ollama ollama = new Ollama();
    private Rag rag = new Rag();

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String chatModel = "llama3.2:1b";
        private String embedModel = "nomic-embed-text";
        private double temperature = 0.2;
        private int timeoutSeconds = 180;
        private int numCtx = 2048;
        private int numPredict = 512;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbedModel() {
            return embedModel;
        }

        public void setEmbedModel(String embedModel) {
            this.embedModel = embedModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getNumCtx() {
            return numCtx;
        }

        public void setNumCtx(int numCtx) {
            this.numCtx = numCtx;
        }

        public int getNumPredict() {
            return numPredict;
        }

        public void setNumPredict(int numPredict) {
            this.numPredict = numPredict;
        }
    }

    public static class Rag {
        private String rootPath = "..";
        private String persistPath = "./data/rag-index.json";
        private boolean autoIndexOnStartup = true;
        private int topK = 6;
        private int chunkChars = 1200;
        private int chunkOverlap = 150;
        private int maxFiles = 400;
        private String includeGlobs = "**/*.java,**/*.yml,**/*.yaml,**/*.md";
        private String excludeGlobs = "**/target/**,**/node_modules/**,**/.git/**";

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getPersistPath() {
            return persistPath;
        }

        public void setPersistPath(String persistPath) {
            this.persistPath = persistPath;
        }

        public boolean isAutoIndexOnStartup() {
            return autoIndexOnStartup;
        }

        public void setAutoIndexOnStartup(boolean autoIndexOnStartup) {
            this.autoIndexOnStartup = autoIndexOnStartup;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getChunkChars() {
            return chunkChars;
        }

        public void setChunkChars(int chunkChars) {
            this.chunkChars = chunkChars;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public String getIncludeGlobs() {
            return includeGlobs;
        }

        public void setIncludeGlobs(String includeGlobs) {
            this.includeGlobs = includeGlobs;
        }

        public String getExcludeGlobs() {
            return excludeGlobs;
        }

        public void setExcludeGlobs(String excludeGlobs) {
            this.excludeGlobs = excludeGlobs;
        }

        public List<String> includeGlobList() {
            return split(includeGlobs);
        }

        public List<String> excludeGlobList() {
            return split(excludeGlobs);
        }

        private List<String> split(String csv) {
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }
    }
}
