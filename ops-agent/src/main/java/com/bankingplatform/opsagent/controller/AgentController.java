package com.bankingplatform.opsagent.controller;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.llm.ReasoningEngineRouter;
import com.bankingplatform.opsagent.mitigation.MitigationService;
import com.bankingplatform.opsagent.model.AgentReport;
import com.bankingplatform.opsagent.model.ChatRequest;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.InvestigateRequest;
import com.bankingplatform.opsagent.model.MitigationAction;
import com.bankingplatform.opsagent.service.IncidentStore;
import com.bankingplatform.opsagent.service.InvestigationService;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.webhook.AlertmanagerWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final InvestigationService investigationService;
    private final IncidentStore incidentStore;
    private final AlertmanagerWebhookService alertmanagerWebhookService;
    private final MitigationService mitigationService;
    private final ToolRegistry toolRegistry;
    private final OpsAgentProperties properties;
    private final ReasoningEngineRouter reasoningEngineRouter;

    public AgentController(InvestigationService investigationService,
                           IncidentStore incidentStore,
                           AlertmanagerWebhookService alertmanagerWebhookService,
                           MitigationService mitigationService,
                           ToolRegistry toolRegistry,
                           OpsAgentProperties properties,
                           ReasoningEngineRouter reasoningEngineRouter) {
        this.investigationService = investigationService;
        this.incidentStore = incidentStore;
        this.alertmanagerWebhookService = alertmanagerWebhookService;
        this.mitigationService = mitigationService;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.reasoningEngineRouter = reasoningEngineRouter;
    }

    @GetMapping("/health-summary")
    public Map<String, Object> healthSummary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platform", properties.getPlatformName());
        out.put("engine", reasoningEngineRouter.name());
        out.put("llmEnabled", properties.getLlm().isEnabled());
        out.put("mitigationMode", properties.getMitigationMode());
        out.put("services", properties.getServices().keySet());
        out.put("tools", toolRegistry.catalog());
        out.put("openIncidents", incidentStore.list().stream()
            .filter(i -> i.getStatus() != Incident.Status.RESOLVED && i.getStatus() != Incident.Status.CLOSED)
            .count());
        return out;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> tools() {
        return toolRegistry.catalog();
    }

    @PostMapping("/investigate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Incident investigate(@Valid @RequestBody InvestigateRequest request) {
        Incident incident = new Incident();
        incident.setSource("manual");
        incident.setTitle(request.getTitle());
        incident.setSummary(request.getDescription());
        incident.setAffectedService(request.getService());
        incident.setCategory(request.getCategory() == null ? "manual" : request.getCategory());
        incident.setSeverity(parseSeverity(request.getSeverity()));
        if (request.getContext() != null) {
            incident.getEvidence().put("context", request.getContext());
        }
        return investigationService.createAndInvestigateAsync(incident);
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() {
        return incidentStore.list();
    }

    @GetMapping("/incidents/{id}")
    public Incident incident(@PathVariable String id) {
        return incidentStore.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @GetMapping("/incidents/{id}/report")
    public AgentReport report(@PathVariable String id) {
        try {
            return investigationService.reportFor(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/incidents/{id}/mitigations/{actionId}/approve")
    public MitigationAction approve(@PathVariable String id, @PathVariable String actionId) {
        Incident incident = incidentStore.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        try {
            MitigationAction action = mitigationService.approveAndExecute(incident, actionId);
            incidentStore.save(incident);
            return action;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/webhooks/alertmanager")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> alertmanagerWebhook(@RequestBody JsonNode payload) {
        Incident incident = alertmanagerWebhookService.handle(payload);
        return Map.of(
            "accepted", true,
            "incidentId", incident.getId(),
            "status", incident.getStatus().name()
        );
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest request) {
        Incident incident;
        if (request.getIncidentId() != null && !request.getIncidentId().isBlank()) {
            incident = incidentStore.findById(request.getIncidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
            incident.setSummary(request.getMessage());
            incidentStore.save(incident);
        } else {
            incident = new Incident();
            incident.setSource("chat");
            incident.setTitle(shortTitle(request.getMessage()));
            incident.setSummary(request.getMessage());
            incident.setCategory("chat");
            incident.setSeverity(Incident.Severity.WARNING);
            incidentStore.save(incident);
        }

        // Chat runs synchronously so the operator gets an immediate answer.
        incident = investigationService.investigate(incident.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("incidentId", incident.getId());
        response.put("status", incident.getStatus());
        response.put("reply", incident.getReportMarkdown() != null
            ? incident.getReportMarkdown()
            : incident.getRootCauseHypothesis());
        response.put("mitigations", incident.getMitigations());
        return response;
    }

    private Incident.Severity parseSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return Incident.Severity.WARNING;
        }
        try {
            return Incident.Severity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Incident.Severity.WARNING;
        }
    }

    private String shortTitle(String message) {
        if (message == null) {
            return "Chat investigation";
        }
        String trimmed = message.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "…";
    }
}
