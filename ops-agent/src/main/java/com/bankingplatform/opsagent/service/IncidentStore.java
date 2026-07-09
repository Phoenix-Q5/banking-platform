package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.model.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IncidentStore {

    private final Map<String, Incident> byId = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprintToIncident = new ConcurrentHashMap<>();

    public Incident save(Incident incident) {
        byId.put(incident.getId(), incident);
        for (String fp : incident.getAlertFingerprints()) {
            fingerprintToIncident.put(fp, incident.getId());
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
