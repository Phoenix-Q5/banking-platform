package com.bankingplatform.opsagent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Incident {

    public enum Status {
        OPEN, INVESTIGATING, MITIGATING, RESOLVED, CLOSED
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    private final String id;
    private Status status;
    private Severity severity;
    private String title;
    private String summary;
    private String category;
    private String affectedService;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private final List<String> alertFingerprints = new ArrayList<>();
    private final List<InvestigationStep> steps = new ArrayList<>();
    private final List<MitigationAction> mitigations = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private String rootCauseHypothesis;
    private String reportMarkdown;
    private String source; // alertmanager | manual | chat

    public Incident() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.OPEN;
        this.severity = Severity.WARNING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.source = "manual";
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        touch();
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
        touch();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        touch();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        touch();
    }

    public String getAffectedService() {
        return affectedService;
    }

    public void setAffectedService(String affectedService) {
        this.affectedService = affectedService;
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
        touch();
    }

    public List<String> getAlertFingerprints() {
        return alertFingerprints;
    }

    public List<InvestigationStep> getSteps() {
        return steps;
    }

    public List<MitigationAction> getMitigations() {
        return mitigations;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public String getRootCauseHypothesis() {
        return rootCauseHypothesis;
    }

    public void setRootCauseHypothesis(String rootCauseHypothesis) {
        this.rootCauseHypothesis = rootCauseHypothesis;
        touch();
    }

    public String getReportMarkdown() {
        return reportMarkdown;
    }

    public void setReportMarkdown(String reportMarkdown) {
        this.reportMarkdown = reportMarkdown;
        touch();
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
