package com.bankingplatform.aiagent.model;

import jakarta.validation.constraints.Size;

public class AnalyzeRequest {

    @Size(max = 500)
    private String focus;

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }
}
