package com.bankingplatform.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope for all Harbor Bank domain events flowing over Kafka.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent {

    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String customerId;
    private Instant occurredAt;
    private String source;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public DomainEvent() {
    }

    public static DomainEvent of(String eventType, String aggregateType, String aggregateId,
                                 String customerId, String source, Map<String, Object> payload) {
        DomainEvent event = new DomainEvent();
        event.eventId = UUID.randomUUID().toString();
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.customerId = customerId;
        event.occurredAt = Instant.now();
        event.source = source;
        if (payload != null) {
            event.payload.putAll(payload);
        }
        return event;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
