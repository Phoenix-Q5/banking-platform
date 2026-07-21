package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.model.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends high-priority ops emails through notification-service
 * (POST /api/notifications/internal/ops-email). Failures are reported to the
 * caller so the console can tell the operator the notification did not go out.
 */
@Service
public class OpsEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(OpsEmailNotifier.class);

    private final WebClient.Builder webClientBuilder;
    private final String notificationBaseUrl;

    public OpsEmailNotifier(WebClient.Builder webClientBuilder,
                            @Value("${ops-agent.notifications.base-url:http://localhost:8087}") String notificationBaseUrl) {
        this.webClientBuilder = webClientBuilder;
        this.notificationBaseUrl = notificationBaseUrl;
    }

    public Map<String, Object> notifyTeam(Incident incident, String message, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject", "[" + incident.getPriority() + "][" + incident.getSeverity() + "] " + safe(incident.getTitle()));
        payload.put("body", buildBody(incident, message, actor));
        payload.put("priority", incident.getPriority().name());
        payload.put("incidentId", incident.getId());
        payload.put("affectedService", incident.getAffectedService());
        return send(payload);
    }

    public Map<String, Object> notifyRestart(String service, String reason, String requestId, String actor, String phase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject", "[P1][RESTART-" + phase + "] Emergency restart of " + service);
        payload.put("body", "Emergency restart " + phase.toLowerCase() + " for service '" + service + "'.\n"
            + "Requested/decided by: " + safe(actor) + "\n"
            + "Reason: " + safe(reason) + "\n"
            + "Restart request id: " + requestId + "\n"
            + "Operator command: docker-compose restart " + service);
        payload.put("priority", "P1");
        payload.put("incidentId", requestId);
        payload.put("affectedService", service);
        return send(payload);
    }

    private Map<String, Object> send(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClientBuilder.baseUrl(notificationBaseUrl).build()
            .post()
            .uri("/api/notifications/internal/ops-email")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(10));
        log.info("ops_email_requested subject={} result={}", payload.get("subject"), response);
        return response == null ? Map.of("delivered", false) : response;
    }

    private String buildBody(Incident incident, String message, String actor) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident: ").append(safe(incident.getTitle())).append("\n");
        sb.append("Id: ").append(incident.getId()).append("\n");
        sb.append("Status: ").append(incident.getStatus()).append("\n");
        sb.append("Priority: ").append(incident.getPriority()).append("\n");
        sb.append("Severity: ").append(incident.getSeverity()).append("\n");
        sb.append("Service: ").append(safe(incident.getAffectedService())).append("\n");
        sb.append("Occurred at: ").append(incident.getOccurredAt()).append("\n");
        if (incident.getRootCauseHypothesis() != null) {
            sb.append("Root cause hypothesis: ").append(incident.getRootCauseHypothesis()).append("\n");
        }
        if (message != null && !message.isBlank()) {
            sb.append("\nOperator note from ").append(safe(actor)).append(":\n").append(message).append("\n");
        }
        sb.append("\nOpen the ops console for the full report: http://localhost:8085");
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }
}
