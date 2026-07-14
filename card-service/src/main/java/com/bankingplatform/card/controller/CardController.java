
package com.bankingplatform.card.controller;

import com.bankingplatform.card.dto.CardDtos.*;
import com.bankingplatform.card.exception.NotFoundException;
import com.bankingplatform.card.model.Card;
import com.bankingplatform.card.messaging.CardEventPublisher;
import com.bankingplatform.card.repository.CardRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.List; import java.util.Locale; import java.util.UUID; import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private static final Logger log = LoggerFactory.getLogger(CardController.class);
    private final CardRepository repository;
    private final CardEventPublisher eventPublisher;
    public CardController(CardRepository repository, CardEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /** Public endpoint — no authentication required. */
    @GetMapping("/offers")
    public List<CardOfferResponse> offers() {
        return List.of(
            new CardOfferResponse(
                "DEBIT", "VISA",
                "Harbor Everyday Debit",
                "Simple, free spending from your checking account",
                List.of(
                    "No annual fee",
                    "Zero foreign transaction fees",
                    "Instant freeze/unfreeze via app",
                    "Real-time purchase notifications",
                    "Up to $500 daily ATM withdrawals"
                ),
                new BigDecimal("500.00"), new BigDecimal("5000.00"),
                "$0", "N/A", "N/A", "N/A"
            ),
            new CardOfferResponse(
                "CREDIT", "VISA",
                "Harbor Rewards Visa",
                "Earn 1.5 % cash back on every purchase",
                List.of(
                    "1.5 % unlimited cash back",
                    "No annual fee",
                    "0 % intro APR for 12 months on purchases",
                    "Travel accident insurance",
                    "24/7 concierge support",
                    "Zero liability fraud protection"
                ),
                new BigDecimal("1000.00"), new BigDecimal("10000.00"),
                "$0", "1.5 % cash back", "0 % for 12 months", "19.99 % – 27.99 % variable"
            ),
            new CardOfferResponse(
                "CREDIT", "MASTERCARD",
                "Harbor Platinum Mastercard",
                "Premium rewards with travel perks",
                List.of(
                    "2 % cash back on travel & dining",
                    "1 % cash back on all other purchases",
                    "$100 annual travel credit",
                    "Global airport lounge access (2 visits/year)",
                    "Cell phone protection up to $800",
                    "Extended warranty on purchases"
                ),
                new BigDecimal("2000.00"), new BigDecimal("20000.00"),
                "$95/year", "Up to 2 % cash back", "0 % for 15 months", "21.49 % – 29.49 % variable"
            ),
            new CardOfferResponse(
                "CREDIT", "AMEX",
                "Harbor Elite Amex",
                "Our most exclusive card with unlimited rewards",
                List.of(
                    "3 % cash back on dining & groceries",
                    "2 % cash back on travel",
                    "1 % cash back on everything else",
                    "Unlimited airport lounge access",
                    "$200 annual statement credit",
                    "Concierge & travel booking service",
                    "Purchase protection up to $10,000",
                    "No foreign transaction fees"
                ),
                new BigDecimal("5000.00"), new BigDecimal("50000.00"),
                "$249/year", "Up to 3 % cash back", "N/A", "24.99 % – 29.99 % variable"
            )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse issue(@Valid @RequestBody IssueCardRequest request) {
        Card card = new Card();
        card.setCustomerId(request.customerId());
        card.setAccountId(request.accountId());
        card.setCardType(Card.CardType.valueOf(request.cardType().toUpperCase(Locale.ROOT)));
        String network = request.cardNetwork() == null ? "VISA" : request.cardNetwork();
        card.setCardNetwork(Card.CardNetwork.valueOf(network.toUpperCase(Locale.ROOT)));
        card.setCardNumberLast4(String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000)));
        card.setDailyLimit(request.dailyLimit() == null ? new BigDecimal("1000.00") : request.dailyLimit());
        card.setMonthlyLimit(request.monthlyLimit() == null ? new BigDecimal("10000.00") : request.monthlyLimit());
        card.setExpiresOn(LocalDate.now().plusYears(3));
        Card saved = repository.save(card);
        log.info("card_issued id={} customerId={} last4={}", saved.getId(), saved.getCustomerId(), saved.getCardNumberLast4());
        eventPublisher.issued(saved);
        return CardResponse.from(saved);
    }

    @GetMapping("/{id}")
    public CardResponse get(@PathVariable("id") UUID id) {
        return CardResponse.from(repository.findById(id).orElseThrow(() -> new NotFoundException("Card not found: " + id)));
    }

    @GetMapping
    public List<CardResponse> list(@RequestParam(name="customerId", required=false) UUID customerId, @RequestParam(name="accountId", required=false) UUID accountId) {
        if (customerId != null) return repository.findByCustomerId(customerId).stream().map(CardResponse::from).toList();
        if (accountId != null) return repository.findByAccountId(accountId).stream().map(CardResponse::from).toList();
        return repository.findAll().stream().map(CardResponse::from).toList();
    }

    @PostMapping("/{id}/freeze")
    public CardResponse freeze(@PathVariable("id") UUID id) {
        Card card = repository.findById(id).orElseThrow(() -> new NotFoundException("Card not found: " + id));
        card.setStatus(Card.Status.FROZEN);
        Card saved = repository.save(card);
        eventPublisher.frozen(saved);
        return CardResponse.from(saved);
    }

    @PostMapping("/{id}/unfreeze")
    public CardResponse unfreeze(@PathVariable("id") UUID id) {
        Card card = repository.findById(id).orElseThrow(() -> new NotFoundException("Card not found: " + id));
        if (card.getStatus() == Card.Status.CANCELLED) throw new IllegalArgumentException("Cancelled cards cannot be unfrozen");
        card.setStatus(Card.Status.ACTIVE);
        Card saved = repository.save(card);
        eventPublisher.unfrozen(saved);
        return CardResponse.from(saved);
    }

    @PutMapping("/{id}/limits")
    public CardResponse updateLimits(@PathVariable("id") UUID id, @Valid @RequestBody UpdateLimitsRequest request) {
        Card card = repository.findById(id).orElseThrow(() -> new NotFoundException("Card not found: " + id));
        card.setDailyLimit(request.dailyLimit());
        card.setMonthlyLimit(request.monthlyLimit());
        return CardResponse.from(repository.save(card));
    }
}
