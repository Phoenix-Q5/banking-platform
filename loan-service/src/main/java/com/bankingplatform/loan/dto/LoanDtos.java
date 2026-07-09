
package com.bankingplatform.loan.dto;
import com.bankingplatform.loan.model.Loan;
import jakarta.validation.constraints.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public final class LoanDtos {
    private LoanDtos(){}
    public record ApplyLoanRequest(
        @NotNull UUID customerId, @NotBlank String productCode,
        @NotNull @DecimalMin("100.00") BigDecimal principal,
        @NotNull @DecimalMin("0.01") BigDecimal interestRate,
        @Min(6) @Max(360) int termMonths,
        @NotBlank @Size(min=3,max=3) String currency,
        String purpose
    ){}
    public record LoanDecisionRequest(@NotBlank String decision){}
    public record LoanResponse(UUID id, UUID customerId, String productCode, BigDecimal principal, BigDecimal interestRate,
                               int termMonths, BigDecimal monthlyPayment, BigDecimal outstandingBalance, String currency,
                               String status, String purpose, Instant createdAt, Instant updatedAt){
        public static LoanResponse from(Loan l){
            return new LoanResponse(l.getId(), l.getCustomerId(), l.getProductCode(), l.getPrincipal(), l.getInterestRate(),
                l.getTermMonths(), l.getMonthlyPayment(), l.getOutstandingBalance(), l.getCurrency(),
                l.getStatus().name(), l.getPurpose(), l.getCreatedAt(), l.getUpdatedAt());
        }
    }
}
