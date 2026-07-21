package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.RestartRequest;
import com.bankingplatform.opsagent.persistence.IncidentPersistence;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emergency restart workflow. The agent never restarts containers itself:
 * an operator raises a request, a (different or same) ADMIN confirms it, and
 * the confirmation is recorded, audited, health-probed, and broadcast to the
 * ops team with the exact operator command. This keeps destructive actions
 * human-executed while making the decision trail auditable.
 */
@Service
public class RestartService {

    private static final Logger log = LoggerFactory.getLogger(RestartService.class);

    private final Map<String, RestartRequest> requests = new ConcurrentHashMap<>();
    private final OpsAgentProperties properties;
    private final IncidentStore incidentStore;
    private final ToolRegistry toolRegistry;
    private final OpsEmailNotifier emailNotifier;
    private final IncidentPersistence persistence;

    public RestartService(OpsAgentProperties properties,
                          IncidentStore incidentStore,
                          ToolRegistry toolRegistry,
                          OpsEmailNotifier emailNotifier,
                          ObjectProvider<IncidentPersistence> persistenceProvider) {
        this.properties = properties;
        this.incidentStore = incidentStore;
        this.toolRegistry = toolRegistry;
        this.emailNotifier = emailNotifier;
        this.persistence = persistenceProvider.getIfAvailable();
        if (persistence != null) {
            for (RestartRequest r : persistence.loadRestartRequests()) {
                requests.put(r.getId(), r);
            }
            log.info("restart_requests_rehydrated count={}", requests.size());
        }
    }

    public List<String> restartableServices() {
        return new ArrayList<>(properties.getServices().keySet());
    }

    public List<RestartRequest> list() {
        List<RestartRequest> all = new ArrayList<>(requests.values());
        all.sort(Comparator.comparing(RestartRequest::getRequestedAt).reversed());
        return all;
    }

    public Optional<RestartRequest> findById(String id) {
        return Optional.ofNullable(requests.get(id));
    }

    public RestartRequest request(String service, String reason, String incidentId, String actor) {
        if (!properties.getServices().containsKey(service)) {
            throw new IllegalArgumentException("Service '" + service + "' is not in the restartable allow-list: "
                + properties.getServices().keySet());
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for an emergency restart request");
        }
        boolean duplicate = requests.values().stream()
            .anyMatch(r -> r.getService().equals(service) && r.getStatus() == RestartRequest.Status.PENDING_CONFIRMATION);
        if (duplicate) {
            throw new IllegalStateException("A pending restart request already exists for " + service);
        }

        RestartRequest request = new RestartRequest();
        request.setService(service);
        request.setReason(reason);
        request.setIncidentId(blankToNull(incidentId));
        request.setRequestedBy(actor);
        save(request);

        auditIncident(incidentId, actor, "RESTART_REQUESTED", "Emergency restart of " + service + " requested: " + reason);
        log.info("restart_requested id={} service={} by={}", request.getId(), service, actor);
        return request;
    }

    public RestartRequest confirm(String requestId, String actor) {
        RestartRequest request = require(requestId);
        if (request.getStatus() != RestartRequest.Status.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Restart request is " + request.getStatus() + ", only pending requests can be confirmed");
        }
        request.setStatus(RestartRequest.Status.CONFIRMED);
        request.setDecidedBy(actor);
        request.setDecidedAt(Instant.now());
        request.setNote(buildConfirmationNote(request));
        save(request);

        auditIncident(request.getIncidentId(), actor, "RESTART_CONFIRMED",
            "Emergency restart of " + request.getService() + " confirmed (request " + request.getId() + ")");
        try {
            emailNotifier.notifyRestart(request.getService(), request.getReason(), request.getId(), actor, "CONFIRMED");
        } catch (Exception ex) {
            log.warn("restart_email_failed id={} reason={}", request.getId(), ex.getMessage());
        }
        log.info("restart_confirmed id={} service={} by={}", request.getId(), request.getService(), actor);
        return request;
    }

    public RestartRequest cancel(String requestId, String actor, String note) {
        RestartRequest request = require(requestId);
        if (request.getStatus() != RestartRequest.Status.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Restart request is " + request.getStatus() + ", only pending requests can be cancelled");
        }
        request.setStatus(RestartRequest.Status.CANCELLED);
        request.setDecidedBy(actor);
        request.setDecidedAt(Instant.now());
        request.setNote(note);
        save(request);

        auditIncident(request.getIncidentId(), actor, "RESTART_CANCELLED",
            "Emergency restart of " + request.getService() + " cancelled (request " + request.getId() + ")");
        log.info("restart_cancelled id={} service={} by={}", request.getId(), request.getService(), actor);
        return request;
    }

    /** Read-only pre-restart health probe so the confirmation records current state. */
    private String buildConfirmationNote(RestartRequest request) {
        String probe;
        try {
            ToolResult result = toolRegistry.invoke("service_health", Map.of("service", request.getService()));
            probe = result.getSummary();
        } catch (Exception ex) {
            probe = "health probe failed: " + ex.getMessage();
        }
        return "Confirmed. Pre-restart health: " + probe
            + " | Operator command: docker-compose restart " + request.getService();
    }

    private void auditIncident(String incidentId, String actor, String action, String detail) {
        if (incidentId == null || incidentId.isBlank()) {
            return;
        }
        incidentStore.findById(incidentId).ifPresent(incident -> {
            incident.audit(actor, action, detail);
            incidentStore.save(incident);
        });
    }

    private void save(RestartRequest request) {
        requests.put(request.getId(), request);
        if (persistence != null) {
            persistence.saveRestartRequest(request);
        }
    }

    private RestartRequest require(String id) {
        RestartRequest request = requests.get(id);
        if (request == null) {
            throw new IllegalArgumentException("Restart request not found: " + id);
        }
        return request;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
