package com.bankingplatform.opsagent.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvestigationStep {

    private Instant timestamp = Instant.now();
    private String tool;
    private Map<String, Object> arguments = new LinkedHashMap<>();
    private String resultSummary;
    private boolean success = true;

    public InvestigationStep() {
    }

    public InvestigationStep(String tool, Map<String, Object> arguments, String resultSummary, boolean success) {
        this.tool = tool;
        if (arguments != null) {
            this.arguments.putAll(arguments);
        }
        this.resultSummary = resultSummary;
        this.success = success;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
