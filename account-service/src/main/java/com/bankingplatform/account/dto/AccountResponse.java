package com.bankingplatform.account.dto;

import com.bankingplatform.account.model.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    UUID customerId,
    BigDecimal balance,
    String currency,
    String status,
    Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.getId(), a.getAccountNumber(), a.getCustomerId(),
            a.getBalance(), a.getCurrency(), a.getStatus().name(), a.getCreatedAt()
        );
    }
}
