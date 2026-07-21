package com.bankingplatform.opsagent.persistence;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.RestartRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * PostgreSQL-backed persistence. Documents are stored as JSONB so the rich
 * incident graph (steps, mitigations, audit trail, evidence) survives
 * restarts without a wide relational mapping.
 */
@Component
@ConditionalOnProperty(prefix = "ops-agent.persistence", name = "enabled", havingValue = "true")
public class JdbcIncidentPersistence implements IncidentPersistence {

    private static final Logger log = LoggerFactory.getLogger(JdbcIncidentPersistence.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIncidentPersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveIncident(Incident incident) {
        String doc = toJson(incident);
        jdbcTemplate.update("""
            INSERT INTO ops_incidents (id, updated_at, doc)
            VALUES (?, ?, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET updated_at = EXCLUDED.updated_at, doc = EXCLUDED.doc
            """, incident.getId(), Timestamp.from(incident.getUpdatedAt()), doc);
    }

    @Override
    public List<Incident> loadIncidents() {
        return jdbcTemplate.query("SELECT doc, updated_at FROM ops_incidents", (rs, rowNum) -> {
            Incident incident = fromJson(rs.getString("doc"), Incident.class);
            // Setter-based deserialization bumps updatedAt via touch(); restore the stored value.
            Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
            incident.setUpdatedAt(updatedAt);
            return incident;
        });
    }

    @Override
    public void saveRestartRequest(RestartRequest request) {
        String doc = toJson(request);
        jdbcTemplate.update("""
            INSERT INTO ops_restart_requests (id, requested_at, doc)
            VALUES (?, ?, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc
            """, request.getId(), Timestamp.from(request.getRequestedAt()), doc);
    }

    @Override
    public List<RestartRequest> loadRestartRequests() {
        return jdbcTemplate.query("SELECT doc FROM ops_restart_requests",
            (rs, rowNum) -> fromJson(rs.getString("doc"), RestartRequest.class));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.error("Failed to deserialize {}: {}", type.getSimpleName(), ex.getMessage());
            throw new IllegalStateException("Corrupt " + type.getSimpleName() + " document", ex);
        }
    }
}
