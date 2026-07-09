package com.bankingplatform.opsagent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentReport {

    private String incidentId;
    private String title;
    private String severity;
    private String affectedService;
    private String status;
    private String executiveSummary;
    private String rootCauseHypothesis;
    private List<String> findings = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private List<String> resiliencyNotes = new ArrayList<>();
    private String markdown;
    private Map<String, Object> evidence = new LinkedHashMap<>();

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getAffectedService() {
        return affectedService;
    }

    public void setAffectedService(String affectedService) {
        this.affectedService = affectedService;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
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

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence;
    }
}
