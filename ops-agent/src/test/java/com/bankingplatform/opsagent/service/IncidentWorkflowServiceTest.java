package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.model.Incident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentWorkflowServiceTest {

    private IncidentStore store;
    private IncidentWorkflowService service;
    private Incident incident;

    @BeforeEach
    void setUp() {
        store = new IncidentStore();
        service = new IncidentWorkflowService(store);
        incident = new Incident();
        incident.setTitle("account-service latency");
        incident.setSeverity(Incident.Severity.CRITICAL);
        store.save(incident);
    }

    @Test
    void changesStatusAndAudits() {
        Incident updated = service.changeStatus(incident.getId(), Incident.Status.INVESTIGATING, "demo.admin", "taking a look");

        assertEquals(Incident.Status.INVESTIGATING, updated.getStatus());
        assertEquals(1, updated.getAuditTrail().size());
        assertEquals("demo.admin", updated.getAuditTrail().get(0).getActor());
        assertEquals("STATUS_CHANGED", updated.getAuditTrail().get(0).getAction());
    }

    @Test
    void resolvingSetsResolvedAtAndReopeningClearsIt() {
        service.changeStatus(incident.getId(), Incident.Status.RESOLVED, "demo.admin", null);
        assertNotNull(incident.getResolvedAt());
        assertEquals("demo.admin", incident.getResolvedBy());

        service.changeStatus(incident.getId(), Incident.Status.OPEN, "demo.admin", "regression");
        assertNull(incident.getResolvedAt());
    }

    @Test
    void rejectsIllegalTransition() {
        service.changeStatus(incident.getId(), Incident.Status.CLOSED, "demo.admin", null);
        assertThrows(IllegalArgumentException.class,
            () -> service.changeStatus(incident.getId(), Incident.Status.MITIGATING, "demo.admin", null));
    }

    @Test
    void priorityDefaultsFromSeverityAndUpgrades() {
        // CRITICAL defaults to P1, so upgrade is a no-op at the top of the scale.
        assertEquals(Incident.Priority.P1, incident.getPriority());
        service.changePriority(incident.getId(), "upgrade", null, "demo.admin");
        assertEquals(Incident.Priority.P1, incident.getPriority());

        service.changePriority(incident.getId(), "downgrade", null, "demo.admin");
        assertEquals(Incident.Priority.P2, incident.getPriority());
        assertTrue(incident.getAuditTrail().stream().anyMatch(a -> "PRIORITY_CHANGED".equals(a.getAction())));
    }

    @Test
    void explicitPriorityWins() {
        service.changePriority(incident.getId(), null, Incident.Priority.P4, "demo.support");
        assertEquals(Incident.Priority.P4, incident.getPriority());
    }

    @Test
    void recordsResolutionNotesAndMarksResolved() {
        Incident updated = service.recordResolution(incident.getId(), "Restarted pod, cleared connection pool", true, "demo.support");

        assertEquals("Restarted pod, cleared connection pool", updated.getResolutionNotes());
        assertEquals("demo.support", updated.getResolvedBy());
        assertEquals(Incident.Status.RESOLVED, updated.getStatus());
        assertNotNull(updated.getResolvedAt());
    }

    @Test
    void rejectsBlankResolutionNotes() {
        assertThrows(IllegalArgumentException.class,
            () -> service.recordResolution(incident.getId(), "  ", true, "demo.admin"));
    }

    @Test
    void unknownIncidentIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.changeStatus("nope", Incident.Status.CLOSED, "demo.admin", null));
    }
}
