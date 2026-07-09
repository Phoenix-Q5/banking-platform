package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GenericDegradationPlaybook implements MitigationPlaybook {

    @Override
    public String id() {
        return "generic-degradation";
    }

    @Override
    public String description() {
        return "Default playbook when category is unclear.";
    }

    @Override
    public boolean supports(Incident incident, String hint) {
        return true;
    }

    @Override
    public List<MitigationAction> propose(Incident incident) {
        return List.of(
            action("Confirm customer impact via gateway error rates and contact-center tickets.", true),
            action("Collect health + metrics + logs + traces into the incident evidence pack.", true),
            action("Escalate to on-call if severity is CRITICAL or impact > 5 minutes.", false),
            action("Document timeline and next check-in time.", false)
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
}
