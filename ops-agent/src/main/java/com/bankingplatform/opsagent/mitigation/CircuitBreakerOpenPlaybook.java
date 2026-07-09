package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class CircuitBreakerOpenPlaybook implements MitigationPlaybook {

    @Override
    public String id() {
        return "circuit-breaker-open";
    }

    @Override
    public String description() {
        return "Handle OPEN circuit breakers safely without cascading failures.";
    }

    @Override
    public boolean supports(Incident incident, String hint) {
        if (id().equals(hint)) {
            return true;
        }
        String blob = (safe(incident.getTitle()) + " " + safe(incident.getCategory()) + " " + safe(incident.getSummary()))
            .toLowerCase(Locale.ROOT);
        return blob.contains("circuit") || blob.contains("resiliency");
    }

    @Override
    public List<MitigationAction> propose(Incident incident) {
        return List.of(
            action("Identify downstream dependency behind the open breaker (usually account-service).", true),
            action("Check downstream /actuator/health and recent 5xx/latency before any reset.", true),
            action("Allow half-open probes; do not force-close breaker under active failures.", false),
            action("If downstream recovered, confirm CLOSED state and successful canary transfers.", false),
            action("Review timeout/retry budgets after recovery to prevent flapping.", false)
        );
    }

    private MitigationAction action(String description, boolean automated) {
        MitigationAction a = new MitigationAction();
        a.setId(UUID.randomUUID().toString());
        a.setPlaybook(id());
        a.setDescription(description);
        a.setAutomated(automated);
        a.setStatus(MitigationAction.Status.PROPOSED);
        return a;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
