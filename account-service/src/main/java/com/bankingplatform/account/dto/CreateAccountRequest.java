package com.bankingplatform.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAccountRequest(
    @NotNull UUID customerId,
    @NotNull @Size(min = 3, max = 3) String currency
) {}
