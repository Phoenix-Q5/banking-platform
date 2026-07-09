package com.bankingplatform.audit.messaging;

import com.bankingplatform.events.BankingTopics;
import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.audit.model.AuditEvent;
import com.bankingplatform.audit.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "harbor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventAuditListener.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public DomainEventAuditListener(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = BankingTopics.DOMAIN_EVENTS, groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void onMessage(String payload) {
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            AuditEvent audit = new AuditEvent();
            audit.setActor(event.getSource() == null ? "system" : event.getSource());
            audit.setAction(event.getEventType());
            audit.setResourceType(event.getAggregateType());
            audit.setResourceId(event.getAggregateId());
            if (event.getCustomerId() != null && !event.getCustomerId().isBlank()) {
                audit.setCustomerId(UUID.fromString(event.getCustomerId()));
            }
            audit.setDetails(objectMapper.writeValueAsString(event.getPayload()));
            repository.save(audit);
            log.info("audit_from_event type={} aggregate={}:{}", event.getEventType(), event.getAggregateType(), event.getAggregateId());
        } catch (Exception ex) {
            log.error("audit_event_consume_failed reason={}", ex.getMessage());
        }
    }
}
