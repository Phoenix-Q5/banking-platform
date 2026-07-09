
package com.bankingplatform.loan.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.events.kafka.DomainEventPublisher;
import com.bankingplatform.loan.model.Loan;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LoanEventPublisher {
    private final DomainEventPublisher publisher;
    public LoanEventPublisher(DomainEventPublisher publisher) { this.publisher = publisher; }

    public void applied(Loan loan) {
        publish(EventTypes.LOAN_APPLIED, loan, null);
    }

    public void statusChanged(Loan loan, String previousStatus) {
        publish(EventTypes.LOAN_STATUS_CHANGED, loan, previousStatus);
    }

    private void publish(String type, Loan loan, String previousStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loanId", loan.getId().toString());
        payload.put("productCode", loan.getProductCode());
        payload.put("principal", loan.getPrincipal());
        payload.put("currency", loan.getCurrency());
        payload.put("status", loan.getStatus().name());
        if (previousStatus != null) payload.put("previousStatus", previousStatus);
        publisher.publish(DomainEvent.of(type, "loan", loan.getId().toString(),
            loan.getCustomerId().toString(), "loan-service", payload));
    }
}
