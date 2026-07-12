
package com.bankingplatform.payment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {
    public enum Status { ACTIVE, DISABLED }

    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false) private String nickname;
    @Column(name = "account_number", nullable = false) private String accountNumber;
    @Column(name = "routing_number") private String routingNumber;
    @Column(name = "bank_name") private String bankName;
    @Column(nullable = false, length = 3) private String currency = "USD";
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.ACTIVE;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
    public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
