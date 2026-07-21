package com.bankingplatform.opsagent.webhook;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.metrics.OpsAgentMetrics;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.service.IncidentStore;
import com.bankingplatform.opsagent.service.InvestigationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AlertmanagerWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AlertmanagerWebhookService.class);

    private final IncidentStore incidentStore;
    private final InvestigationService investigationService;
    private final OpsAgentProperties properties;
    private final OpsAgentMetrics opsAgentMetrics;

    public AlertmanagerWebhookService(IncidentStore incidentStore,
                                      InvestigationService investigationService,
                                      OpsAgentProperties properties,
                                      OpsAgentMetrics opsAgentMetrics) {
        this.incidentStore = incidentStore;
        this.investigationService = investigationService;
        this.properties = properties;
        this.opsAgentMetrics = opsAgentMetrics;
    }

    public Incident handle(JsonNode payload) {
        String status = payload.path("status").asText("firing");
        JsonNode alerts = payload.path("alerts");
        if (!alerts.isArray() || alerts.isEmpty()) {
            throw new IllegalArgumentException("Alertmanager payload missing alerts[]");
        }

        // Record every alert in the payload for metrics.
        for (JsonNode alert : alerts) {
            opsAgentMetrics.recordAlertReceived(
                    alert.path("labels").path("alertname").asText("unknown"),
                    alert.path("labels").path("severity").asText("unknown"),
                    alert.path("status").asText(status));
        }

        // Process the first firing alert as the primary signal; attach all fingerprints.
        JsonNode primary = null;
        for (JsonNode alert : alerts) {
            if ("firing".equalsIgnoreCase(alert.path("status").asText(status))) {
                primary = alert;
                break;
            }
        }
        if (primary == null) {
            primary = alerts.get(0);
        }

        String fingerprint = primary.path("fingerprint").asText(primary.path("labels").path("alertname").asText());
        Optional<Incident> existing = incidentStore.findByFingerprint(fingerprint);

        if ("resolved".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(primary.path("status").asText())) {
            if (existing.isPresent()) {
                Incident incident = existing.get();
                incident.setStatus(Incident.Status.RESOLVED);
                incident.setSummary((incident.getSummary() == null ? "" : incident.getSummary() + " | ")
                    + "Alertmanager marked alert resolved.");
                incidentStore.save(incident);
                return incident;
            }
            Incident resolved = new Incident();
            resolved.setSource("alertmanager");
            resolved.setTitle(primary.path("annotations").path("summary").asText("Resolved alert"));
            resolved.setStatus(Incident.Status.RESOLVED);
            resolved.getAlertFingerprints().add(fingerprint);
            return incidentStore.save(resolved);
        }

        if (existing.isPresent() && existing.get().getStatus() != Incident.Status.RESOLVED
            && existing.get().getStatus() != Incident.Status.CLOSED) {
            log.info("Deduped alert fingerprint={} to incident={}", fingerprint, existing.get().getId());
            return existing.get();
        }

        Incident incident = new Incident();
        incident.setSource("alertmanager");
        incident.setTitle(primary.path("annotations").path("summary")
            .asText(primary.path("labels").path("alertname").asText("Alert")));
        incident.setSummary(primary.path("annotations").path("description").asText(""));
        incident.setCategory(primary.path("labels").path("category").asText("ops"));
        incident.setAffectedService(firstNonBlank(
            primary.path("labels").path("service").asText(null),
            primary.path("labels").path("application").asText(null),
            primary.path("labels").path("job").asText(null)
        ));
        incident.setSeverity(mapSeverity(primary.path("labels").path("severity").asText("warning")));
        parseInstant(primary.path("startsAt").asText(null)).ifPresent(incident::setOccurredAt);
        incident.getAlertFingerprints().add(fingerprint);
        for (JsonNode alert : alerts) {
            String fp = alert.path("fingerprint").asText(null);
            if (fp != null && !incident.getAlertFingerprints().contains(fp)) {
                incident.getAlertFingerprints().add(fp);
            }
        }
        incident.getEvidence().put("alertmanagerPayload", payload);

        opsAgentMetrics.recordIncidentCreated(incident.getSource(),
                incident.getSeverity() != null ? incident.getSeverity().name() : null,
                incident.getCategory());

        if (properties.isAutoInvestigate()) {
            return investigationService.createAndInvestigateAsync(incident);
        }
        return incidentStore.save(incident);
    }

    private Incident.Severity mapSeverity(String severity) {
        String s = severity == null ? "warning" : severity.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "critical", "error", "page" -> Incident.Severity.CRITICAL;
            case "info", "none" -> Incident.Severity.INFO;
            default -> Incident.Severity.WARNING;
        };
    }

    private Optional<java.time.Instant> parseInstant(String iso) {
        if (iso == null || iso.isBlank() || iso.startsWith("0001-")) {
            return Optional.empty();
        }
        try {
            return Optional.of(java.time.OffsetDateTime.parse(iso).toInstant());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)) {
                return v;
            }
        }
        return null;
    }
}
