package com.bankingplatform.opsagent.webhook;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.service.IncidentStore;
import com.bankingplatform.opsagent.service.InvestigationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertmanagerWebhookServiceTest {

    @Mock
    private InvestigationService investigationService;

    private IncidentStore incidentStore;
    private AlertmanagerWebhookService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        incidentStore = new IncidentStore();
        OpsAgentProperties properties = new OpsAgentProperties();
        properties.setAutoInvestigate(true);
        service = new AlertmanagerWebhookService(incidentStore, investigationService, properties, null);
    }

    @Test
    void createsIncidentFromFiringAlert() throws Exception {
        when(investigationService.createAndInvestigateAsync(any())).thenAnswer(inv -> {
            Incident incident = inv.getArgument(0);
            return incidentStore.save(incident);
        });

        ObjectNode payload = mapper.createObjectNode();
        payload.put("status", "firing");
        ArrayNode alerts = payload.putArray("alerts");
        ObjectNode alert = alerts.addObject();
        alert.put("status", "firing");
        alert.put("fingerprint", "fp-circuit-1");
        ObjectNode labels = alert.putObject("labels");
        labels.put("alertname", "CircuitBreakerOpen");
        labels.put("severity", "critical");
        labels.put("category", "resiliency");
        labels.put("application", "transaction-service");
        ObjectNode annotations = alert.putObject("annotations");
        annotations.put("summary", "Circuit breaker open on transaction-service");
        annotations.put("description", "Calls are failing fast");

        Incident incident = service.handle(payload);

        assertEquals(Incident.Severity.CRITICAL, incident.getSeverity());
        assertEquals("transaction-service", incident.getAffectedService());
        assertEquals("resiliency", incident.getCategory());
        assertTrue(incident.getAlertFingerprints().contains("fp-circuit-1"));
        assertEquals("alertmanager", incident.getSource());
    }

    @Test
    void deduplicatesByFingerprint() throws Exception {
        when(investigationService.createAndInvestigateAsync(any())).thenAnswer(inv -> {
            Incident incident = inv.getArgument(0);
            incident.setStatus(Incident.Status.INVESTIGATING);
            return incidentStore.save(incident);
        });

        ObjectNode payload = basePayload("fp-dup");
        Incident first = service.handle(payload);
        Incident second = service.handle(payload);

        assertEquals(first.getId(), second.getId());
        assertEquals(1, incidentStore.list().size());
    }

    private ObjectNode basePayload(String fingerprint) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("status", "firing");
        ArrayNode alerts = payload.putArray("alerts");
        ObjectNode alert = alerts.addObject();
        alert.put("status", "firing");
        alert.put("fingerprint", fingerprint);
        ObjectNode labels = alert.putObject("labels");
        labels.put("alertname", "ServiceDown");
        labels.put("severity", "critical");
        labels.put("service", "account-service");
        ObjectNode annotations = alert.putObject("annotations");
        annotations.put("summary", "Service account-service is down");
        return payload;
    }
}
