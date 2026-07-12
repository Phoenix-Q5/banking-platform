package com.bankingplatform.transaction.dto;

import com.bankingplatform.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    String status,
    String failureReason,
    Instant createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
            t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(),
            t.getCurrency(), t.getStatus().name(), t.getFailureReason(), t.getCreatedAt()
        );
    }
}
