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

    /** Operational priority, orderable so it can be upgraded (towards P1) or downgraded (towards P4). */
    public enum Priority {
        P1, P2, P3, P4;

        public Priority upgraded() {
            return ordinal() == 0 ? this : values()[ordinal() - 1];
        }

        public Priority downgraded() {
            return ordinal() == values().length - 1 ? this : values()[ordinal() + 1];
        }

        public static Priority defaultFor(Severity severity) {
            if (severity == null) {
                return P3;
            }
            return switch (severity) {
                case CRITICAL -> P1;
                case WARNING -> P3;
                case INFO -> P4;
            };
        }
    }

    private String id;
    private Status status;
    private Severity severity;
    private Priority priority;
    private String title;
    private String summary;
    private String category;
    private String affectedService;
    private Instant createdAt;
    private Instant updatedAt;
    /** When the underlying failure actually started (alert startsAt / operator supplied), vs createdAt = record creation. */
    private Instant occurredAt;
    private Instant resolvedAt;
    private String resolutionNotes;
    private String resolvedBy;
    private final List<String> alertFingerprints = new ArrayList<>();
    private final List<InvestigationStep> steps = new ArrayList<>();
    private final List<MitigationAction> mitigations = new ArrayList<>();
    private final List<AuditEntry> auditTrail = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private String rootCauseHypothesis;
    private String reportMarkdown;
    private String source; // alertmanager | manual | chat

    public Incident() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.OPEN;
        this.severity = Severity.WARNING;
        // priority stays null until explicitly set; getPriority() derives it from severity
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.occurredAt = this.createdAt;
        this.source = "manual";
    }

    public String getId() {
        return id;
    }

    /** Only intended for persistence rehydration (JSON deserialization). */
    public void setId(String id) {
        this.id = id;
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

    public Priority getPriority() {
        return priority == null ? Priority.defaultFor(severity) : priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
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

    /** Only intended for persistence rehydration. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Only intended for persistence rehydration; does not touch(). */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
        touch();
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
        touch();
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
        touch();
    }

    public List<AuditEntry> getAuditTrail() {
        return auditTrail;
    }

    public void audit(String actor, String action, String detail) {
        auditTrail.add(new AuditEntry(actor, action, detail));
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
