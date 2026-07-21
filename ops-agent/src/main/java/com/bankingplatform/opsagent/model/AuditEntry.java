package com.bankingplatform.opsagent.model;

import java.time.Instant;

/**
 * Immutable-ish audit record attached to an incident. Every privileged
 * operation (status/priority change, resolution, notification, restart,
 * mitigation approval) appends one of these.
 */
public class AuditEntry {

    private Instant timestamp = Instant.now();
    private String actor;
    private String action;
    private String detail;

    public AuditEntry() {
    }

    public AuditEntry(String actor, String action, String detail) {
        this.actor = actor;
        this.action = action;
        this.detail = detail;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
