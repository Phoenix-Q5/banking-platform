package com.bankingplatform.opsagent.controller;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.RestartRequest;
import com.bankingplatform.opsagent.service.IncidentStore;
import com.bankingplatform.opsagent.service.IncidentWorkflowService;
import com.bankingplatform.opsagent.service.OpsEmailNotifier;
import com.bankingplatform.opsagent.service.RestartService;
import com.bankingplatform.opsagent.service.ServiceAlertBridge;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

/**
 * Incident workflow (status, priority, resolution, team notification) and the
 * emergency restart request/confirm/cancel loop.
 */
@RestController
@RequestMapping("/api/agent")
public class IncidentActionsController {

    private static final Logger log = LoggerFactory.getLogger(IncidentActionsController.class);

    private final IncidentWorkflowService workflowService;
    private final IncidentStore incidentStore;
    private final RestartService restartService;
    private final OpsEmailNotifier emailNotifier;
    private final ServiceAlertBridge serviceAlertBridge;

    public IncidentActionsController(IncidentWorkflowService workflowService,
                                     IncidentStore incidentStore,
                                     RestartService restartService,
                                     OpsEmailNotifier emailNotifier,
                                     ServiceAlertBridge serviceAlertBridge) {
        this.workflowService = workflowService;
        this.incidentStore = incidentStore;
        this.restartService = restartService;
        this.emailNotifier = emailNotifier;
        this.serviceAlertBridge = serviceAlertBridge;
    }

    public record StatusChangeRequest(@NotBlank String status, String note) {}
    public record PriorityChangeRequest(String direction, String priority) {}
    public record ResolutionRequest(@NotBlank String notes, boolean markResolved) {}
    public record NotifyRequest(String message) {}
    public record RestartCreateRequest(@NotBlank String service, @NotBlank String reason, String incidentId) {}
    public record RestartCancelRequest(String note) {}

    @PostMapping("/incidents/{id}/status")
    public Incident changeStatus(@PathVariable("id") String id,
                                 @RequestBody StatusChangeRequest request,
                                 Authentication authentication) {
        Incident.Status target = parseEnum(Incident.Status.class, request.status(), "status");
        try {
            return workflowService.changeStatus(id, target, actor(authentication), request.note());
        } catch (IllegalArgumentException ex) {
            throw toHttp(ex);
        }
    }

    @PostMapping("/incidents/{id}/priority")
    public Incident changePriority(@PathVariable("id") String id,
                                   @RequestBody PriorityChangeRequest request,
                                   Authentication authentication) {
        Incident.Priority explicit = request.priority() == null ? null
            : parseEnum(Incident.Priority.class, request.priority(), "priority");
        try {
            return workflowService.changePriority(id, request.direction(), explicit, actor(authentication));
        } catch (IllegalArgumentException ex) {
            throw toHttp(ex);
        }
    }

    @PostMapping("/incidents/{id}/resolution")
    public Incident recordResolution(@PathVariable("id") String id,
                                     @RequestBody ResolutionRequest request,
                                     Authentication authentication) {
        try {
            return workflowService.recordResolution(id, request.notes(), request.markResolved(), actor(authentication));
        } catch (IllegalArgumentException ex) {
            throw toHttp(ex);
        }
    }

    @PostMapping("/incidents/{id}/notify")
    public Map<String, Object> notifyTeam(@PathVariable("id") String id,
                                          @RequestBody(required = false) NotifyRequest request,
                                          Authentication authentication) {
        Incident incident = incidentStore.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        String actor = actor(authentication);
        String message = request == null ? null : request.message();

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> emailResult = emailNotifier.notifyTeam(incident, message, actor);
            out.put("email", emailResult);
            out.put("delivered", true);
            incident.audit(actor, "TEAM_NOTIFIED", "High-priority email requested" + (message == null ? "" : ": " + message));
        } catch (Exception ex) {
            log.warn("notify_team_failed incidentId={} reason={}", id, ex.getMessage());
            out.put("delivered", false);
            out.put("error", "Email dispatch failed: " + ex.getMessage());
            incident.audit(actor, "TEAM_NOTIFY_FAILED", ex.getMessage());
        }
        // Push/in-app alert to registered admin devices, best-effort.
        serviceAlertBridge.publish(incident);
        incidentStore.save(incident);
        return out;
    }

    @GetMapping("/restart-requests")
    public List<RestartRequest> restartRequests() {
        return restartService.list();
    }

    @GetMapping("/restartable-services")
    public List<String> restartableServices() {
        return restartService.restartableServices();
    }

    @PostMapping("/restart-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RestartRequest createRestartRequest(@RequestBody RestartCreateRequest request,
                                               Authentication authentication) {
        try {
            return restartService.request(request.service(), request.reason(), request.incidentId(), actor(authentication));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw toHttp(ex);
        }
    }

    @PostMapping("/restart-requests/{id}/confirm")
    public RestartRequest confirmRestart(@PathVariable("id") String id, Authentication authentication) {
        try {
            return restartService.confirm(id, actor(authentication));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw toHttp(ex);
        }
    }

    @PostMapping("/restart-requests/{id}/cancel")
    public RestartRequest cancelRestart(@PathVariable("id") String id,
                                        @RequestBody(required = false) RestartCancelRequest request,
                                        Authentication authentication) {
        try {
            return restartService.cancel(id, actor(authentication), request == null ? null : request.note());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw toHttp(ex);
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null ? "console" : authentication.getName();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + ": " + value);
        }
    }

    private ResponseStatusException toHttp(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "Request failed" : ex.getMessage();
        if (msg.contains("not found")) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
        }
        if (ex instanceof IllegalStateException) {
            return new ResponseStatusException(HttpStatus.CONFLICT, msg);
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
