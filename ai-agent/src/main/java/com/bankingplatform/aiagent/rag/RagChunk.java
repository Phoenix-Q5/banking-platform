package com.bankingplatform.aiagent.rag;

import java.util.Arrays;

public class RagChunk {

    private String id;
    private String path;
    private int startLine;
    private int endLine;
    private String text;
    private float[] embedding;

    public RagChunk() {
    }

    public RagChunk(String id, String path, int startLine, int endLine, String text, float[] embedding) {
        this.id = id;
        this.path = path;
        this.startLine = startLine;
        this.endLine = endLine;
        this.text = text;
        this.embedding = embedding;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "RagChunk{id='" + id + "', path='" + path + "', lines=" + startLine + "-" + endLine
            + ", dims=" + (embedding == null ? 0 : embedding.length) + "}";
    }

    public RagChunk copyWithoutEmbedding() {
        return new RagChunk(id, path, startLine, endLine, text, null);
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RagChunk other)) {
            return false;
        }
        return id != null && id.equals(other.id) && Arrays.equals(embedding, other.embedding);
    }
}
