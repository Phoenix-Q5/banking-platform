package com.bankingplatform.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceAdjustmentRequest(
    @NotNull UUID transactionId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
