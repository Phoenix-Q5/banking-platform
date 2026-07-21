package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.model.Incident;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Human-in-the-loop incident lifecycle operations: status transitions,
 * priority upgrades/downgrades, and resolution recording. Every change is
 * appended to the incident audit trail with the acting operator.
 */
@Service
public class IncidentWorkflowService {

    private static final Map<Incident.Status, Set<Incident.Status>> ALLOWED_TRANSITIONS = Map.of(
        Incident.Status.OPEN, Set.of(Incident.Status.INVESTIGATING, Incident.Status.MITIGATING, Incident.Status.RESOLVED, Incident.Status.CLOSED),
        Incident.Status.INVESTIGATING, Set.of(Incident.Status.OPEN, Incident.Status.MITIGATING, Incident.Status.RESOLVED, Incident.Status.CLOSED),
        Incident.Status.MITIGATING, Set.of(Incident.Status.OPEN, Incident.Status.INVESTIGATING, Incident.Status.RESOLVED, Incident.Status.CLOSED),
        Incident.Status.RESOLVED, Set.of(Incident.Status.OPEN, Incident.Status.CLOSED),
        Incident.Status.CLOSED, Set.of(Incident.Status.OPEN)
    );

    private final IncidentStore incidentStore;

    public IncidentWorkflowService(IncidentStore incidentStore) {
        this.incidentStore = incidentStore;
    }

    public Incident changeStatus(String incidentId, Incident.Status target, String actor, String note) {
        Incident incident = require(incidentId);
        Incident.Status current = incident.getStatus();
        if (current == target) {
            return incident;
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalArgumentException("Illegal status transition " + current + " -> " + target);
        }
        incident.setStatus(target);
        if (target == Incident.Status.RESOLVED) {
            incident.setResolvedAt(Instant.now());
            if (actor != null) {
                incident.setResolvedBy(actor);
            }
        }
        if (target == Incident.Status.OPEN) {
            // Reopened: previous resolution no longer stands.
            incident.setResolvedAt(null);
        }
        incident.audit(actor, "STATUS_CHANGED", current + " -> " + target + (blank(note) ? "" : " (" + note + ")"));
        return incidentStore.save(incident);
    }

    public Incident changePriority(String incidentId, String direction, Incident.Priority explicit, String actor) {
        Incident incident = require(incidentId);
        Incident.Priority current = incident.getPriority();
        Incident.Priority target;
        if (explicit != null) {
            target = explicit;
        } else if ("upgrade".equalsIgnoreCase(direction)) {
            target = current.upgraded();
        } else if ("downgrade".equalsIgnoreCase(direction)) {
            target = current.downgraded();
        } else {
            throw new IllegalArgumentException("direction must be 'upgrade' or 'downgrade', or provide an explicit priority");
        }
        if (target == current) {
            return incident;
        }
        incident.setPriority(target);
        incident.audit(actor, "PRIORITY_CHANGED", current + " -> " + target);
        return incidentStore.save(incident);
    }

    public Incident recordResolution(String incidentId, String notes, boolean markResolved, String actor) {
        if (blank(notes)) {
            throw new IllegalArgumentException("Resolution notes must not be blank");
        }
        Incident incident = require(incidentId);
        incident.setResolutionNotes(notes);
        incident.setResolvedBy(actor);
        if (markResolved && incident.getStatus() != Incident.Status.RESOLVED && incident.getStatus() != Incident.Status.CLOSED) {
            incident.setStatus(Incident.Status.RESOLVED);
            incident.setResolvedAt(Instant.now());
        }
        incident.audit(actor, "RESOLUTION_RECORDED", markResolved ? "Marked resolved with notes" : "Notes updated");
        return incidentStore.save(incident);
    }

    private Incident require(String incidentId) {
        return incidentStore.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
