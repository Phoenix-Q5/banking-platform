package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybookSelectionTest {

    @Test
    void serviceDownPlaybookMatchesAvailabilityIncidents() {
        ServiceDownPlaybook playbook = new ServiceDownPlaybook();
        Incident incident = new Incident();
        incident.setTitle("ServiceDown");
        incident.setCategory("availability");
        incident.setAffectedService("account-service");

        assertTrue(playbook.supports(incident, "service-down"));
        List<MitigationAction> actions = playbook.propose(incident);
        assertFalse(actions.isEmpty());
        assertTrue(actions.stream().anyMatch(a -> a.getDescription().contains("account-service")));
    }

    @Test
    void circuitBreakerPlaybookMatchesResiliency() {
        CircuitBreakerOpenPlaybook playbook = new CircuitBreakerOpenPlaybook();
        Incident incident = new Incident();
        incident.setTitle("Circuit breaker open");
        incident.setCategory("resiliency");

        assertTrue(playbook.supports(incident, null));
        assertFalse(playbook.propose(incident).isEmpty());
    }
}
