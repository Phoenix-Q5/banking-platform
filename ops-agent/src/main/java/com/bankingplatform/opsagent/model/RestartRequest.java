package com.bankingplatform.opsagent.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Emergency restart request. Restarts are never executed by the agent itself:
 * a request must be raised and then explicitly confirmed by an ADMIN, at which
 * point it is recorded, audited, and the ops team is notified with the exact
 * command to run. This keeps a human in the loop for destructive actions.
 */
public class RestartRequest {

    public enum Status {
        PENDING_CONFIRMATION, CONFIRMED, CANCELLED
    }

    private String id;
    private String service;
    private String reason;
    private String incidentId;
    private Status status;
    private String requestedBy;
    private Instant requestedAt;
    private String decidedBy;
    private Instant decidedAt;
    private String note;

    public RestartRequest() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.PENDING_CONFIRMATION;
        this.requestedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
