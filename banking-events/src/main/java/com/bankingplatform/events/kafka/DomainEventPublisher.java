package com.bankingplatform.events.kafka;

import com.bankingplatform.events.BankingTopics;
import com.bankingplatform.events.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Thin publisher used by Harbor Bank services to emit domain events.
 */
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public DomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publish(DomainEvent event) {
        if (!enabled || kafkaTemplate == null) {
            log.debug("kafka_publish_skipped eventType={} reason=disabled", event.getEventType());
            return;
        }
        try {
            String key = event.getAggregateId() != null ? event.getAggregateId() : event.getEventId();
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(BankingTopics.DOMAIN_EVENTS, key, json);
            log.info("domain_event_published type={} aggregate={}:{} customerId={}",
                event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getCustomerId());
        } catch (Exception ex) {
            // Domain write already succeeded — log and continue (prototype; production would use outbox).
            log.error("domain_event_publish_failed type={} reason={}", event.getEventType(), ex.getMessage());
        }
    }
}
