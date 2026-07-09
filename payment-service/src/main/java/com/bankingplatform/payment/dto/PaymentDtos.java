
package com.bankingplatform.payment.dto;

import com.bankingplatform.payment.model.Beneficiary;
import com.bankingplatform.payment.model.Payment;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CreateBeneficiaryRequest(
        @NotNull UUID customerId,
        @NotBlank String nickname,
        @NotBlank String accountNumber,
        String routingNumber,
        String bankName,
        @Size(min=3,max=3) String currency
    ) {}

    public record BeneficiaryResponse(UUID id, UUID customerId, String nickname, String accountNumber,
                                      String routingNumber, String bankName, String currency, String status, Instant createdAt) {
        public static BeneficiaryResponse from(Beneficiary b) {
            return new BeneficiaryResponse(b.getId(), b.getCustomerId(), b.getNickname(), b.getAccountNumber(),
                b.getRoutingNumber(), b.getBankName(), b.getCurrency(), b.getStatus().name(), b.getCreatedAt());
        }
    }

    public record CreatePaymentRequest(
        @NotNull UUID customerId,
        @NotNull UUID fromAccountId,
        UUID beneficiaryId,
        @NotBlank String paymentType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min=3,max=3) String currency,
        String reference,
        String description,
        LocalDate scheduledFor
    ) {}

    public record PaymentResponse(UUID id, UUID customerId, UUID fromAccountId, UUID beneficiaryId, String paymentType,
                                  BigDecimal amount, String currency, String status, String reference, String description,
                                  LocalDate scheduledFor, String failureReason, Instant createdAt, Instant updatedAt) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getCustomerId(), p.getFromAccountId(), p.getBeneficiaryId(),
                p.getPaymentType().name(), p.getAmount(), p.getCurrency(), p.getStatus().name(), p.getReference(),
                p.getDescription(), p.getScheduledFor(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
