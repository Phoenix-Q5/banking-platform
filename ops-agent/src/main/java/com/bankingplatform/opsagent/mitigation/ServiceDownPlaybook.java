package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class ServiceDownPlaybook implements MitigationPlaybook {

    @Override
    public String id() {
        return "service-down";
    }

    @Override
    public String description() {
        return "Restore availability for a downed microservice and protect callers.";
    }

    @Override
    public boolean supports(Incident incident, String hint) {
        if (id().equals(hint)) {
            return true;
        }
        String blob = (safe(incident.getTitle()) + " " + safe(incident.getCategory()) + " " + safe(incident.getSummary()))
            .toLowerCase(Locale.ROOT);
        return blob.contains("down") || blob.contains("availability") || blob.contains("unreachable");
    }

    @Override
    public List<MitigationAction> propose(Incident incident) {
        String service = incident.getAffectedService() == null ? "unknown-service" : incident.getAffectedService();
        return List.of(
            action("Verify container/pod health and restart " + service + " if crashed.", false),
            action("Confirm Postgres health and connection pool for " + service + ".", false),
            action("Validate api-gateway fallback returns DEGRADED for " + service + " routes.", true),
            action("Notify contact-center / status page of partial outage.", false),
            action("Block non-essential deploys until service is green for 15 minutes.", false)
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
