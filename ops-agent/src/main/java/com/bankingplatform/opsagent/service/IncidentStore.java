package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.persistence.IncidentPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory incident store with optional PostgreSQL write-through.
 * When persistence is enabled the map is rehydrated on startup so
 * incidents survive restarts.
 */
@Component
public class IncidentStore {

    private static final Logger log = LoggerFactory.getLogger(IncidentStore.class);

    private final Map<String, Incident> byId = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprintToIncident = new ConcurrentHashMap<>();
    private final IncidentPersistence persistence;

    /** Test-friendly constructor (no persistence). */
    public IncidentStore() {
        this.persistence = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentStore(ObjectProvider<IncidentPersistence> persistenceProvider) {
        this.persistence = persistenceProvider.getIfAvailable();
        if (persistence != null) {
            for (Incident incident : persistence.loadIncidents()) {
                byId.put(incident.getId(), incident);
                for (String fp : incident.getAlertFingerprints()) {
                    fingerprintToIncident.put(fp, incident.getId());
                }
            }
            log.info("incidents_rehydrated count={}", byId.size());
        }
    }

    public Incident save(Incident incident) {
        byId.put(incident.getId(), incident);
        for (String fp : incident.getAlertFingerprints()) {
            fingerprintToIncident.put(fp, incident.getId());
        }
        if (persistence != null) {
            try {
                persistence.saveIncident(incident);
            } catch (Exception ex) {
                log.warn("incident_persist_failed id={} reason={}", incident.getId(), ex.getMessage());
            }
        }
        return incident;
    }

    public Optional<Incident> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Incident> findByFingerprint(String fingerprint) {
        String id = fingerprintToIncident.get(fingerprint);
        return id == null ? Optional.empty() : findById(id);
    }

    public List<Incident> list() {
        List<Incident> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparing(Incident::getUpdatedAt).reversed());
        return all;
    }

    public void clear() {
        byId.clear();
        fingerprintToIncident.clear();
    }
}
