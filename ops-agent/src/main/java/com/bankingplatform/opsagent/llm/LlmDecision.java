package com.bankingplatform.opsagent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One turn from the reasoning engine — either a tool call or a final answer.
 */
public class LlmDecision {

    public enum Kind {
        TOOL_CALL, FINAL
    }

    private Kind kind;
    private String toolName;
    private Map<String, Object> toolArguments = new LinkedHashMap<>();
    private String finalAnswer;
    private String rootCauseHypothesis;
    private List<String> findings = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private List<String> resiliencyNotes = new ArrayList<>();
    private String playbookHint;

    public static LlmDecision toolCall(String name, Map<String, Object> args) {
        LlmDecision d = new LlmDecision();
        d.kind = Kind.TOOL_CALL;
        d.toolName = name;
        if (args != null) {
            d.toolArguments.putAll(args);
        }
        return d;
    }

    public static LlmDecision finale(String answer) {
        LlmDecision d = new LlmDecision();
        d.kind = Kind.FINAL;
        d.finalAnswer = answer;
        return d;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(Map<String, Object> toolArguments) {
        this.toolArguments = toolArguments;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getRootCauseHypothesis() {
        return rootCauseHypothesis;
    }

    public void setRootCauseHypothesis(String rootCauseHypothesis) {
        this.rootCauseHypothesis = rootCauseHypothesis;
    }

    public List<String> getFindings() {
        return findings;
    }

    public void setFindings(List<String> findings) {
        this.findings = findings;
    }

    public List<String> getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(List<String> recommendedActions) {
        this.recommendedActions = recommendedActions;
    }

    public List<String> getResiliencyNotes() {
        return resiliencyNotes;
    }

    public void setResiliencyNotes(List<String> resiliencyNotes) {
        this.resiliencyNotes = resiliencyNotes;
    }

    public String getPlaybookHint() {
        return playbookHint;
    }

    public void setPlaybookHint(String playbookHint) {
        this.playbookHint = playbookHint;
    }
}
