package com.bankingplatform.opsagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates the JSONB persistence assumption: an incident with the full graph
 * (steps, mitigations, audit trail, evidence) survives an ObjectMapper
 * round-trip using the same mapper configuration as AppConfig.
 */
class IncidentJsonRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void incidentSurvivesRoundTrip() throws Exception {
        Incident incident = new Incident();
        incident.setTitle("transaction-service errors");
        incident.setSummary("5xx spike");
        incident.setSeverity(Incident.Severity.CRITICAL);
        incident.setPriority(Incident.Priority.P2);
        incident.setStatus(Incident.Status.MITIGATING);
        incident.setAffectedService("transaction-service");
        incident.setOccurredAt(Instant.parse("2026-07-21T14:00:00Z"));
        incident.setResolutionNotes("rolled back deploy");
        incident.setResolvedBy("demo.admin");
        incident.getAlertFingerprints().add("fp-1");
        incident.getSteps().add(new InvestigationStep("prometheus_query", Map.of("query", "up"), "1 series", true));
        MitigationAction action = new MitigationAction();
        action.setId("mit-1");
        action.setDescription("Check downstream health");
        action.setPlaybook("high-error-rate");
        incident.getMitigations().add(action);
        incident.audit("demo.admin", "STATUS_CHANGED", "OPEN -> MITIGATING");
        incident.getEvidence().put("key", "value");

        String json = mapper.writeValueAsString(incident);
        Incident back = mapper.readValue(json, Incident.class);

        assertEquals(incident.getId(), back.getId());
        assertEquals(Incident.Status.MITIGATING, back.getStatus());
        assertEquals(Incident.Priority.P2, back.getPriority());
        assertEquals(Incident.Severity.CRITICAL, back.getSeverity());
        assertEquals(incident.getOccurredAt(), back.getOccurredAt());
        assertEquals("rolled back deploy", back.getResolutionNotes());
        assertEquals("demo.admin", back.getResolvedBy());
        assertEquals(1, back.getAlertFingerprints().size());
        assertEquals(1, back.getSteps().size());
        assertEquals("mit-1", back.getMitigations().get(0).getId());
        assertEquals(1, back.getAuditTrail().size());
        assertEquals("STATUS_CHANGED", back.getAuditTrail().get(0).getAction());
        assertEquals("value", back.getEvidence().get("key"));
    }

    @Test
    void restartRequestSurvivesRoundTrip() throws Exception {
        RestartRequest request = new RestartRequest();
        request.setService("account-service");
        request.setReason("DB pool exhausted");
        request.setRequestedBy("demo.support");
        request.setStatus(RestartRequest.Status.CONFIRMED);
        request.setDecidedBy("demo.admin");
        request.setDecidedAt(Instant.parse("2026-07-21T15:00:00Z"));

        String json = mapper.writeValueAsString(request);
        RestartRequest back = mapper.readValue(json, RestartRequest.class);

        assertEquals(request.getId(), back.getId());
        assertEquals(RestartRequest.Status.CONFIRMED, back.getStatus());
        assertEquals("demo.admin", back.getDecidedBy());
        assertEquals(request.getDecidedAt(), back.getDecidedAt());
    }
}
