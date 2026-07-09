package com.bankingplatform.notification.messaging;

import com.bankingplatform.events.BankingTopics;
import com.bankingplatform.events.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "harbor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventNotificationListener.class);

    private final AlertNotificationService alertNotificationService;
    private final ObjectMapper objectMapper;

    public DomainEventNotificationListener(AlertNotificationService alertNotificationService,
                                           ObjectMapper objectMapper) {
        this.alertNotificationService = alertNotificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = BankingTopics.DOMAIN_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onMessage(String payload) {
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            alertNotificationService.handleDomainEvent(event);
        } catch (Exception ex) {
            log.error("domain_event_consume_failed reason={} payload={}", ex.getMessage(), truncate(payload));
        }
    }

    private String truncate(String payload) {
        if (payload == null) return "";
        return payload.length() <= 500 ? payload : payload.substring(0, 500) + "…";
    }
}
