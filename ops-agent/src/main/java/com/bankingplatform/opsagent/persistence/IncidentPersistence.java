package com.bankingplatform.opsagent.persistence;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.RestartRequest;

import java.util.List;

/**
 * Optional durable storage behind the in-memory stores. Present only when
 * ops-agent.persistence.enabled=true (PostgreSQL-backed in production).
 */
public interface IncidentPersistence {

    void saveIncident(Incident incident);

    List<Incident> loadIncidents();

    void saveRestartRequest(RestartRequest request);

    List<RestartRequest> loadRestartRequests();
}
