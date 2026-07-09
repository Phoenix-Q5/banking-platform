
package com.bankingplatform.card.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {
    public enum CardType { DEBIT, CREDIT }
    public enum CardNetwork { VISA, MASTERCARD, AMEX }
    public enum Status { ACTIVE, FROZEN, CANCELLED, EXPIRED }

    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "card_number_last4", nullable = false, length = 4) private String cardNumberLast4;
    @Enumerated(EnumType.STRING) @Column(name = "card_network", nullable = false) private CardNetwork cardNetwork;
    @Enumerated(EnumType.STRING) @Column(name = "card_type", nullable = false) private CardType cardType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.ACTIVE;
    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4) private BigDecimal dailyLimit;
    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4) private BigDecimal monthlyLimit;
    @Column(name = "expires_on", nullable = false) private LocalDate expiresOn;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getCardNumberLast4() { return cardNumberLast4; }
    public void setCardNumberLast4(String cardNumberLast4) { this.cardNumberLast4 = cardNumberLast4; }
    public CardNetwork getCardNetwork() { return cardNetwork; }
    public void setCardNetwork(CardNetwork cardNetwork) { this.cardNetwork = cardNetwork; }
    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(BigDecimal monthlyLimit) { this.monthlyLimit = monthlyLimit; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
