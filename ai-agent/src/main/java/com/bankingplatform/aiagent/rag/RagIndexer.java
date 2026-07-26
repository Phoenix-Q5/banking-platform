package com.bankingplatform.aiagent.rag;

import com.bankingplatform.aiagent.config.AiAgentProperties;
import com.bankingplatform.aiagent.ollama.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RagIndexer {

    private static final Logger log = LoggerFactory.getLogger(RagIndexer.class);

    private final AiAgentProperties properties;
    private final OllamaClient ollamaClient;
    private final InMemoryVectorStore store;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public RagIndexer(AiAgentProperties properties, OllamaClient ollamaClient, InMemoryVectorStore store) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
        this.store = store;
    }

    public boolean isIndexing() {
        return indexing.get();
    }

    public Map<String, Object> indexRepository() {
        if (!indexing.compareAndSet(false, true)) {
            return Map.of("status", "BUSY", "message", "Indexing already in progress");
        }
        long started = System.currentTimeMillis();
        try {
            Path root = Path.of(properties.getRag().getRootPath()).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return Map.of("status", "ERROR", "message", "RAG root not found: " + root);
            }
            if (!ollamaClient.isReachable()) {
                return Map.of(
                    "status", "ERROR",
                    "message", "Ollama is not reachable at " + properties.getOllama().getBaseUrl()
                        + ". Install Ollama and pull chat + embed models first."
                );
            }

            List<Path> files;
            try {
                files = collectFiles(root);
            } catch (IOException ex) {
                return Map.of("status", "ERROR", "message", "Failed to walk repo: " + ex.getMessage());
            }
            List<RagChunk> chunks = new ArrayList<>();
            int fileCount = 0;
            for (Path file : files) {
                fileCount++;
                try {
                    chunks.addAll(chunkFile(root, file));
                } catch (Exception ex) {
                    log.warn("Skip {}: {}", file, ex.getMessage());
                }
            }

            List<RagChunk> embedded = new ArrayList<>();
            int i = 0;
            for (RagChunk chunk : chunks) {
                i++;
                try {
                    float[] vector = ollamaClient.embed(chunk.getPath() + "\n" + chunk.getText());
                    chunk.setEmbedding(vector);
                    embedded.add(chunk);
                    if (i % 25 == 0) {
                        log.info("Embedded {}/{} chunks", i, chunks.size());
                    }
                } catch (Exception ex) {
                    log.warn("Embed failed for {}: {}", chunk.getId(), ex.getMessage());
                }
            }

            store.replaceAll(embedded);
            store.persist();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "OK");
            out.put("root", root.toString());
            out.put("filesIndexed", fileCount);
            out.put("chunks", embedded.size());
            out.put("elapsedMs", System.currentTimeMillis() - started);
            out.put("chatModel", properties.getOllama().getChatModel());
            out.put("embedModel", properties.getOllama().getEmbedModel());
            return out;
        } finally {
            indexing.set(false);
        }
    }

    private List<Path> collectFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        List<String> includes = properties.getRag().includeGlobList();
        List<String> excludes = properties.getRag().excludeGlobList();
        int maxFiles = Math.max(1, properties.getRag().getMaxFiles());

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String rel = relativize(root, dir);
                if (!rel.isEmpty() && excluded(rel + "/", excludes)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (files.size() >= maxFiles) {
                    return FileVisitResult.TERMINATE;
                }
                String rel = relativize(root, file);
                if (excluded(rel, excludes)) {
                    return FileVisitResult.CONTINUE;
                }
                if (included(rel, includes)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private List<RagChunk> chunkFile(Path root, Path file) throws IOException {
        String rel = relativize(root, file);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return List.of();
        }
        // Skip huge generated blobs
        if (content.length() > 250_000) {
            content = content.substring(0, 250_000);
        }

        String[] lines = content.split("\n", -1);
        int chunkChars = Math.max(400, properties.getRag().getChunkChars());
        int overlap = Math.max(0, Math.min(properties.getRag().getChunkOverlap(), chunkChars / 2));

        List<RagChunk> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int startLine = 1;
        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (buf.length() + line.length() + 1 > chunkChars && buf.length() > 0) {
                out.add(newChunk(rel, startLine, lineNo - 1, buf.toString()));
                String carry = overlap == 0 ? "" : tail(buf.toString(), overlap);
                buf.setLength(0);
                buf.append(carry);
                startLine = Math.max(1, lineNo - countLines(carry));
            }
            if (!buf.isEmpty()) {
                buf.append('\n');
            }
            buf.append(line);
        }
        if (!buf.isEmpty()) {
            out.add(newChunk(rel, startLine, Math.max(startLine, lineNo), buf.toString()));
        }
        return out;
    }

    private RagChunk newChunk(String path, int start, int end, String text) {
        String id = UUID.nameUUIDFromBytes((path + ":" + start + ":" + end).getBytes(StandardCharsets.UTF_8)).toString();
        return new RagChunk(id, path, start, end, text.trim(), null);
    }

    private boolean included(String rel, List<String> includes) {
        if (includes.isEmpty()) {
            return true;
        }
        for (String glob : includes) {
            if (matcher.match(normalizeGlob(glob), rel) || matcher.match(normalizeGlob(glob), "/" + rel)) {
                return true;
            }
        }
        return false;
    }

    private boolean excluded(String rel, List<String> excludes) {
        for (String glob : excludes) {
            if (matcher.match(normalizeGlob(glob), rel) || matcher.match(normalizeGlob(glob), "/" + rel)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeGlob(String glob) {
        return glob.startsWith("/") ? glob.substring(1) : glob;
    }

    private String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private String tail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
