package com.bankingplatform.opsagent.model;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank
    private String message;
    private String incidentId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }
}
