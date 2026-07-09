package com.bankingplatform.opsagent.mitigation;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.MitigationAction;

import java.util.List;

public interface MitigationPlaybook {

    String id();

    String description();

    boolean supports(Incident incident, String hint);

    List<MitigationAction> propose(Incident incident);
}
