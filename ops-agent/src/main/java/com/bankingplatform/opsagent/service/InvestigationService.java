package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.llm.LlmDecision;
import com.bankingplatform.opsagent.llm.ReasoningEngineRouter;
import com.bankingplatform.opsagent.mitigation.MitigationService;
import com.bankingplatform.opsagent.model.AgentReport;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.InvestigationStep;
import com.bankingplatform.opsagent.model.MitigationAction;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final IncidentStore incidentStore;
    private final ToolRegistry toolRegistry;
    private final ReasoningEngineRouter reasoningEngine;
    private final MitigationService mitigationService;
    private final OpsAgentProperties properties;
    private final ReportBuilder reportBuilder;
    private final Executor investigationExecutor;
    private final ServiceAlertBridge serviceAlertBridge;

    public InvestigationService(IncidentStore incidentStore,
                                ToolRegistry toolRegistry,
                                ReasoningEngineRouter reasoningEngine,
                                MitigationService mitigationService,
                                OpsAgentProperties properties,
                                ReportBuilder reportBuilder,
                                @Qualifier("investigationExecutor") Executor investigationExecutor,
                                ServiceAlertBridge serviceAlertBridge) {
        this.incidentStore = incidentStore;
        this.toolRegistry = toolRegistry;
        this.reasoningEngine = reasoningEngine;
        this.mitigationService = mitigationService;
        this.properties = properties;
        this.reportBuilder = reportBuilder;
        this.investigationExecutor = investigationExecutor;
        this.serviceAlertBridge = serviceAlertBridge;
    }

    public Incident createAndInvestigateAsync(Incident incident) {
        incidentStore.save(incident);
        CompletableFuture.runAsync(() -> {
            try {
                investigate(incident.getId());
            } catch (Exception ex) {
                log.error("Investigation failed for {}: {}", incident.getId(), ex.getMessage(), ex);
                incidentStore.findById(incident.getId()).ifPresent(i -> {
                    i.setStatus(Incident.Status.OPEN);
                    i.setSummary((i.getSummary() == null ? "" : i.getSummary() + " | ") + "Investigation error: " + ex.getMessage());
                    incidentStore.save(i);
                });
            }
        }, investigationExecutor);
        return incident;
    }

    public Incident investigate(String incidentId) {
        Incident incident = incidentStore.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        incident.setStatus(Incident.Status.INVESTIGATING);
        incidentStore.save(incident);

        List<ToolResult> priorResults = new ArrayList<>();
        List<Map<String, Object>> catalog = toolRegistry.catalog();
        int maxRounds = Math.max(1, properties.getMaxToolRounds());
        LlmDecision finalDecision = null;

        for (int round = 0; round < maxRounds; round++) {
            LlmDecision decision = reasoningEngine.next(incident, catalog, priorResults, round);
            if (decision.getKind() == LlmDecision.Kind.FINAL) {
                finalDecision = decision;
                break;
            }

            Map<String, Object> args = decision.getToolArguments() == null
                ? Map.of()
                : new LinkedHashMap<>(decision.getToolArguments());
            ToolResult result = toolRegistry.invoke(decision.getToolName(), args);
            priorResults.add(result);

            InvestigationStep step = new InvestigationStep(
                decision.getToolName(),
                args,
                result.getSummary(),
                result.isSuccess()
            );
            incident.getSteps().add(step);
            incident.getEvidence().put(decision.getToolName() + "-" + round, truncate(result.getData()));
            if (incident.getAffectedService() == null || incident.getAffectedService().isBlank()) {
                Object svc = args.get("service");
                if (svc != null) {
                    incident.setAffectedService(svc.toString());
                }
            }
            incidentStore.save(incident);
        }

        if (finalDecision == null) {
            finalDecision = reasoningEngine.next(incident, catalog, priorResults, maxRounds);
        }

        applyDecision(incident, finalDecision);
        incident.setStatus(Incident.Status.MITIGATING);
        mitigationService.propose(incident, finalDecision.getPlaybookHint());
        mitigationService.maybeAutoExecute(incident);

        boolean criticalOpen = incident.getSeverity() == Incident.Severity.CRITICAL
            && incident.getMitigations().stream().anyMatch(m -> m.getStatus() == MitigationAction.Status.PROPOSED);
        if (!criticalOpen) {
            incident.setStatus(Incident.Status.RESOLVED);
            incident.setResolvedAt(Instant.now());
        } else {
            incident.setStatus(Incident.Status.MITIGATING);
        }

        AgentReport report = reportBuilder.build(incident, finalDecision);
        incident.setReportMarkdown(report.getMarkdown());
        incident.setRootCauseHypothesis(finalDecision.getRootCauseHypothesis());
        incidentStore.save(incident);
        serviceAlertBridge.publish(incident);
        return incident;
    }

    public AgentReport reportFor(String incidentId) {
        Incident incident = incidentStore.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
        LlmDecision synthetic = LlmDecision.finale(incident.getReportMarkdown() == null ? "" : incident.getReportMarkdown());
        synthetic.setRootCauseHypothesis(incident.getRootCauseHypothesis());
        synthetic.setFindings(incident.getSteps().stream().map(InvestigationStep::getResultSummary).toList());
        synthetic.setRecommendedActions(incident.getMitigations().stream().map(MitigationAction::getDescription).toList());
        return reportBuilder.build(incident, synthetic);
    }

    private void applyDecision(Incident incident, LlmDecision decision) {
        if (decision.getRootCauseHypothesis() != null) {
            incident.setRootCauseHypothesis(decision.getRootCauseHypothesis());
        }
        if (decision.getFinalAnswer() != null) {
            incident.setReportMarkdown(decision.getFinalAnswer());
            incident.setSummary(shorten(decision.getRootCauseHypothesis() != null
                ? decision.getRootCauseHypothesis()
                : decision.getFinalAnswer(), 240));
        }
        if (incident.getAffectedService() == null) {
            incident.setAffectedService(guessService(incident));
        }
    }

    private String guessService(Incident incident) {
        String blob = (safe(incident.getTitle()) + " " + safe(incident.getSummary())).toLowerCase(Locale.ROOT);
        if (blob.contains("account")) {
            return "account-service";
        }
        if (blob.contains("gateway")) {
            return "api-gateway";
        }
        return "transaction-service";
    }

    private Object truncate(Object data) {
        if (data == null) {
            return null;
        }
        String s = String.valueOf(data);
        if (s.length() <= 4000) {
            return data;
        }
        return s.substring(0, 4000) + "…";
    }

    private String shorten(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
