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
 * Bridges ops-agent incidents to notification-service so ADMIN/SUPPORT mobile
 * devices receive Harbor Bank service alerts (PUSH + IN_APP).
 */
@Service
public class ServiceAlertBridge {

    private static final Logger log = LoggerFactory.getLogger(ServiceAlertBridge.class);

    private final WebClient.Builder webClientBuilder;
    private final String notificationBaseUrl;
    private final boolean enabled;

    public ServiceAlertBridge(WebClient.Builder webClientBuilder,
                              @Value("${ops-agent.notifications.base-url:http://localhost:8087}") String notificationBaseUrl,
                              @Value("${ops-agent.notifications.service-alerts-enabled:true}") boolean enabled) {
        this.webClientBuilder = webClientBuilder;
        this.notificationBaseUrl = notificationBaseUrl;
        this.enabled = enabled;
    }

    public void publish(Incident incident) {
        if (!enabled) {
            return;
        }
        try {
            String title = incident.getTitle() == null ? "Harbor Bank service alert" : incident.getTitle();
            String body = incident.getRootCauseHypothesis() != null
                ? incident.getRootCauseHypothesis()
                : (incident.getSummary() == null ? "Service incident detected" : incident.getSummary());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", "[Harbor Ops] " + title);
            payload.put("body", truncate(body, 500));
            payload.put("category", incident.getCategory() == null ? "SERVICE" : "SERVICE_" + incident.getCategory().toUpperCase());
            payload.put("severity", incident.getSeverity() == null ? "WARNING" : incident.getSeverity().name());
            payload.put("incidentId", incident.getId());
            payload.put("affectedService", incident.getAffectedService());

            webClientBuilder.baseUrl(notificationBaseUrl).build()
                .post()
                .uri("/api/notifications/internal/service-alert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5));

            log.info("service_alert_published incidentId={} severity={} service={}",
                incident.getId(), incident.getSeverity(), incident.getAffectedService());
        } catch (Exception ex) {
            log.warn("service_alert_publish_failed incidentId={} reason={}", incident.getId(), ex.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
