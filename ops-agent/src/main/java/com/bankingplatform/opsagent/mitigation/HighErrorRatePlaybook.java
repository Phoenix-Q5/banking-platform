package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class HighErrorRatePlaybook implements MitigationPlaybook {

    @Override
    public String id() {
        return "high-error-rate";
    }

    @Override
    public String description() {
        return "Contain and remediate elevated 5xx / application error rates.";
    }

    @Override
    public boolean supports(Incident incident, String hint) {
        if (id().equals(hint) || "high-latency".equals(hint)) {
            return true;
        }
        String blob = (safe(incident.getTitle()) + " " + safe(incident.getCategory()) + " " + safe(incident.getSummary()))
            .toLowerCase(Locale.ROOT);
        return blob.contains("error") || blob.contains("5xx") || blob.contains("latency");
    }

    @Override
    public List<MitigationAction> propose(Incident incident) {
        String service = incident.getAffectedService() == null ? "affected service" : incident.getAffectedService();
        return List.of(
            action("Group Loki ERROR logs for " + service + " by exception signature.", true),
            action("Correlate failing endpoints with Tempo error traces.", true),
            action("If onset matches a deploy, prepare rollback of " + service + ".", false),
            action("Enable/confirm gateway rate limiting on hot failing paths if abuse suspected.", false),
            action("Communicate ETA and workaround to contact-center agents.", false)
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
