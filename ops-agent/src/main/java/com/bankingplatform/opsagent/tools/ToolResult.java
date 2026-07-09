package com.bankingplatform.opsagent.tools;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolResult {

    private final boolean success;
    private final String summary;
    private final Object data;
    private final Map<String, Object> metadata;

    public ToolResult(boolean success, String summary, Object data) {
        this(success, summary, data, Map.of());
    }

    public ToolResult(boolean success, String summary, Object data, Map<String, Object> metadata) {
        this.success = success;
        this.summary = summary;
        this.data = data;
        this.metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }

    public static ToolResult ok(String summary, Object data) {
        return new ToolResult(true, summary, data);
    }

    public static ToolResult fail(String summary) {
        return new ToolResult(false, summary, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public Object getData() {
        return data;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
