
package com.bankingplatform.card.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.events.kafka.DomainEventPublisher;
import com.bankingplatform.card.model.Card;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CardEventPublisher {
    private final DomainEventPublisher publisher;
    public CardEventPublisher(DomainEventPublisher publisher) { this.publisher = publisher; }

    public void issued(Card card) { publish(EventTypes.CARD_ISSUED, card); }
    public void frozen(Card card) { publish(EventTypes.CARD_FROZEN, card); }
    public void unfrozen(Card card) { publish(EventTypes.CARD_UNFROZEN, card); }

    private void publish(String type, Card card) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cardId", card.getId().toString());
        payload.put("accountId", card.getAccountId().toString());
        payload.put("last4", card.getCardNumberLast4());
        payload.put("cardType", card.getCardType().name());
        payload.put("status", card.getStatus().name());
        publisher.publish(DomainEvent.of(type, "card", card.getId().toString(),
            card.getCustomerId().toString(), "card-service", payload));
    }
}
