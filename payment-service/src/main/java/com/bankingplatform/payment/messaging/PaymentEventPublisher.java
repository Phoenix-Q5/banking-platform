
package com.bankingplatform.payment.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.events.kafka.DomainEventPublisher;
import com.bankingplatform.payment.model.Payment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PaymentEventPublisher {
    private final DomainEventPublisher publisher;
    public PaymentEventPublisher(DomainEventPublisher publisher) { this.publisher = publisher; }

    public void paymentCompleted(Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId().toString());
        payload.put("paymentType", payment.getPaymentType().name());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("fromAccountId", payment.getFromAccountId().toString());
        payload.put("status", payment.getStatus().name());
        payload.put("reference", payment.getReference());
        publisher.publish(DomainEvent.of(
            EventTypes.PAYMENT_COMPLETED, "payment", payment.getId().toString(),
            payment.getCustomerId().toString(), "payment-service", payload));
    }
}
