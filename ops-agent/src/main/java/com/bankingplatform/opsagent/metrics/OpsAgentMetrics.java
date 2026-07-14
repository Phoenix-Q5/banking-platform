package com.bankingplatform.opsagent.metrics;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.service.IncidentStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Exposes ops-agent operational metrics via Micrometer so they are scraped
 * by Prometheus and can be visualised in the Grafana ops-agent admin dashboard.
 *
 * Gauges (read incident store at scrape time):
 *   ops_agent_incidents          {status=open|investigating|mitigating|resolved|closed}
 *   ops_agent_incidents_open     (unresolved count, convenience gauge)
 *
 * Counters (incremented on events):
 *   ops_agent_incidents_created_total   {source, severity, category}
 *   ops_agent_alerts_received_total     {alertname, severity, alert_status}
 *   ops_agent_mitigations_approved_total {playbook}
 *   ops_agent_mitigations_executed_total {playbook, success}
 */
@Component
public class OpsAgentMetrics {

    private final MeterRegistry registry;

    public OpsAgentMetrics(IncidentStore incidentStore, MeterRegistry registry) {
        this.registry = registry;

        for (Incident.Status status : Incident.Status.values()) {
            final Incident.Status s = status;
            Gauge.builder("ops.agent.incidents", incidentStore,
                            store -> store.list().stream()
                                    .filter(i -> i.getStatus() == s)
                                    .count())
                    .tag("status", s.name().toLowerCase())
                    .description("Current number of incidents by status")
                    .register(registry);
        }

        Gauge.builder("ops.agent.incidents.open", incidentStore,
                        store -> store.list().stream()
                                .filter(i -> i.getStatus() != Incident.Status.RESOLVED
                                        && i.getStatus() != Incident.Status.CLOSED)
                                .count())
                .description("Total unresolved incidents (OPEN + INVESTIGATING + MITIGATING)")
                .register(registry);
    }

    public void recordIncidentCreated(String source, String severity, String category) {
        Counter.builder("ops.agent.incidents.created")
                .tag("source", nvl(source, "unknown"))
                .tag("severity", nvl(severity, "unknown").toLowerCase())
                .tag("category", nvl(category, "unknown"))
                .description("Total incidents created, labelled by origin and classification")
                .register(registry)
                .increment();
    }

    public void recordAlertReceived(String alertname, String severity, String alertStatus) {
        Counter.builder("ops.agent.alerts.received")
                .tag("alertname", nvl(alertname, "unknown"))
                .tag("severity", nvl(severity, "unknown").toLowerCase())
                .tag("alert_status", nvl(alertStatus, "unknown").toLowerCase())
                .description("Total Alertmanager webhook calls received")
                .register(registry)
                .increment();
    }

    public void recordMitigationApproved(String playbook) {
        Counter.builder("ops.agent.mitigations.approved")
                .tag("playbook", nvl(playbook, "unknown"))
                .description("Total mitigation actions approved by an operator")
                .register(registry)
                .increment();
    }

    public void recordMitigationExecuted(String playbook, boolean success) {
        Counter.builder("ops.agent.mitigations.executed")
                .tag("playbook", nvl(playbook, "unknown"))
                .tag("success", String.valueOf(success))
                .description("Total mitigation actions executed (automated or manual acknowledgment)")
                .register(registry)
                .increment();
    }

    private static String nvl(String v, String def) {
        return (v != null && !v.isBlank()) ? v : def;
    }
}
