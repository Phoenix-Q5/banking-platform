
package com.bankingplatform.card.dto;
import com.bankingplatform.card.model.Card;
import jakarta.validation.constraints.*;
import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate; import java.util.UUID;
public final class CardDtos {
    private CardDtos() {}
    public record IssueCardRequest(
        @NotNull UUID customerId, @NotNull UUID accountId,
        @NotBlank String cardType, String cardNetwork,
        @DecimalMin("1.00") BigDecimal dailyLimit, @DecimalMin("1.00") BigDecimal monthlyLimit
    ) {}
    public record UpdateLimitsRequest(@NotNull @DecimalMin("1.00") BigDecimal dailyLimit,
                                      @NotNull @DecimalMin("1.00") BigDecimal monthlyLimit) {}
    public record CardResponse(UUID id, UUID customerId, UUID accountId, String cardNumberLast4, String cardNetwork,
                               String cardType, String status, BigDecimal dailyLimit, BigDecimal monthlyLimit,
                               LocalDate expiresOn, Instant createdAt, Instant updatedAt) {
        public static CardResponse from(Card c) {
            return new CardResponse(c.getId(), c.getCustomerId(), c.getAccountId(), c.getCardNumberLast4(),
                c.getCardNetwork().name(), c.getCardType().name(), c.getStatus().name(), c.getDailyLimit(),
                c.getMonthlyLimit(), c.getExpiresOn(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
