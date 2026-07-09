
package com.bankingplatform.account.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.events.kafka.DomainEventPublisher;
import com.bankingplatform.account.model.Account;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AccountEventPublisher {
    private final DomainEventPublisher publisher;
    public AccountEventPublisher(DomainEventPublisher publisher) { this.publisher = publisher; }

    public void opened(Account account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", account.getId().toString());
        payload.put("accountNumber", account.getAccountNumber());
        payload.put("currency", account.getCurrency());
        payload.put("status", account.getStatus().name());
        publisher.publish(DomainEvent.of(EventTypes.ACCOUNT_OPENED, "account", account.getId().toString(),
            account.getCustomerId().toString(), "account-service", payload));
    }
}
