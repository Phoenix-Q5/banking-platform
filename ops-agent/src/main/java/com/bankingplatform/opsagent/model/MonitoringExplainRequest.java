package com.bankingplatform.opsagent.model;

import jakarta.validation.constraints.Size;

/**
 * Optional focus area for the LLM to explain the live monitoring snapshot.
 */
public class MonitoringExplainRequest {

    /** One of: overview, endpoints, heap, gc, traces, alerts, health — or blank for overview. */
    @Size(max = 64)
    private String focus;

    /** Optional free-text follow-up from the operator. */
    @Size(max = 2000)
    private String message;

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
