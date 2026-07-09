package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MitigationService {

    private static final Logger log = LoggerFactory.getLogger(MitigationService.class);

    private final List<MitigationPlaybook> playbooks;
    private final OpsAgentProperties properties;
    private final ToolRegistry toolRegistry;

    public MitigationService(List<MitigationPlaybook> playbooks,
                             OpsAgentProperties properties,
                             ToolRegistry toolRegistry) {
        this.playbooks = playbooks;
        this.properties = properties;
        this.toolRegistry = toolRegistry;
    }

    public List<MitigationAction> propose(Incident incident, String playbookHint) {
        MitigationPlaybook playbook = select(incident, playbookHint);
        List<MitigationAction> actions = new ArrayList<>(playbook.propose(incident));
        incident.getMitigations().clear();
        incident.getMitigations().addAll(actions);
        return actions;
    }

    public List<MitigationAction> maybeAutoExecute(Incident incident) {
        boolean auto = "auto".equalsIgnoreCase(properties.getMitigationMode());
        List<MitigationAction> executed = new ArrayList<>();
        for (MitigationAction action : incident.getMitigations()) {
            if (!action.isAutomated()) {
                continue;
            }
            if (!auto) {
                action.setStatus(MitigationAction.Status.PROPOSED);
                action.setResult("Awaiting human approval (mitigation-mode=recommend)");
                continue;
            }
            executeSafeAutomatedAction(incident, action);
            executed.add(action);
        }
        return executed;
    }

    public MitigationAction approveAndExecute(Incident incident, String actionId) {
        MitigationAction action = incident.getMitigations().stream()
            .filter(a -> a.getId().equals(actionId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown mitigation action: " + actionId));

        action.setStatus(MitigationAction.Status.APPROVED);
        if (action.isAutomated()) {
            executeSafeAutomatedAction(incident, action);
        } else {
            action.setStatus(MitigationAction.Status.EXECUTED);
            action.setExecutedAt(Instant.now());
            action.setResult("Marked executed (manual step acknowledged by operator)");
        }
        return action;
    }

    private void executeSafeAutomatedAction(Incident incident, MitigationAction action) {
        try {
            String desc = action.getDescription().toLowerCase(Locale.ROOT);
            ToolResult result;
            if (desc.contains("health") || desc.contains("downstream")) {
                String service = inferDownstream(incident, desc);
                result = toolRegistry.invoke("service_health", Map.of("service", service));
            } else if (desc.contains("gateway fallback") || desc.contains("degraded")) {
                result = toolRegistry.invoke("service_health", Map.of("service", "api-gateway"));
            } else if (desc.contains("loki") || desc.contains("error logs")) {
                String service = incident.getAffectedService() == null ? "transaction-service" : incident.getAffectedService();
                result = toolRegistry.invoke("loki_query", Map.of(
                    "query", "{service=\"" + service + "\"} |= \"ERROR\"",
                    "limit", 20
                ));
            } else if (desc.contains("tempo") || desc.contains("traces")) {
                String service = incident.getAffectedService() == null ? "transaction-service" : incident.getAffectedService();
                result = toolRegistry.invoke("tempo_search", Map.of("service", service));
            } else if (desc.contains("customer impact") || desc.contains("evidence")) {
                result = toolRegistry.invoke("prometheus_query", Map.of(
                    "query", "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]))"
                ));
            } else {
                result = ToolResult.ok("Automated check acknowledged: " + action.getDescription(), Map.of());
            }

            action.setStatus(result.isSuccess() ? MitigationAction.Status.EXECUTED : MitigationAction.Status.FAILED);
            action.setExecutedAt(Instant.now());
            action.setResult(result.getSummary());
            incident.getEvidence().put("mitigation-" + action.getId(), result.getData());
        } catch (Exception ex) {
            log.warn("Automated mitigation failed: {}", ex.getMessage());
            action.setStatus(MitigationAction.Status.FAILED);
            action.setExecutedAt(Instant.now());
            action.setResult(ex.getMessage());
        }
    }

    private String inferDownstream(Incident incident, String desc) {
        if (desc.contains("account")) {
            return "account-service";
        }
        if (incident.getAffectedService() != null) {
            return incident.getAffectedService();
        }
        return "account-service";
    }

    private MitigationPlaybook select(Incident incident, String hint) {
        for (MitigationPlaybook playbook : playbooks) {
            if (!"generic-degradation".equals(playbook.id()) && playbook.supports(incident, hint)) {
                return playbook;
            }
        }
        return playbooks.stream()
            .filter(p -> "generic-degradation".equals(p.id()))
            .findFirst()
            .orElse(playbooks.get(0));
    }
}
