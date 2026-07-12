package com.bankingplatform.transaction.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.events.kafka.DomainEventPublisher;
import com.bankingplatform.transaction.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TransferEventPublisher {

    private final DomainEventPublisher publisher;

    public TransferEventPublisher(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void transferCompleted(Transaction txn, UUID fromCustomerId, UUID toCustomerId) {
        Map<String, Object> payload = basePayload(txn);
        payload.put("fromCustomerId", fromCustomerId == null ? null : fromCustomerId.toString());
        payload.put("toCustomerId", toCustomerId == null ? null : toCustomerId.toString());
        publisher.publish(DomainEvent.of(
            EventTypes.TRANSFER_COMPLETED,
            "transaction",
            txn.getId().toString(),
            fromCustomerId == null ? (toCustomerId == null ? null : toCustomerId.toString()) : fromCustomerId.toString(),
            "transaction-service",
            payload
        ));
    }

    public void transferFailed(Transaction txn, UUID fromCustomerId, String reason) {
        Map<String, Object> payload = basePayload(txn);
        payload.put("fromCustomerId", fromCustomerId == null ? null : fromCustomerId.toString());
        payload.put("failureReason", reason);
        publisher.publish(DomainEvent.of(
            EventTypes.TRANSFER_FAILED,
            "transaction",
            txn.getId().toString(),
            fromCustomerId == null ? null : fromCustomerId.toString(),
            "transaction-service",
            payload
        ));
    }

    private Map<String, Object> basePayload(Transaction txn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txn.getId().toString());
        payload.put("fromAccountId", txn.getFromAccountId().toString());
        payload.put("toAccountId", txn.getToAccountId().toString());
        payload.put("amount", txn.getAmount());
        payload.put("currency", txn.getCurrency());
        payload.put("status", txn.getStatus().name());
        return payload;
    }
}
