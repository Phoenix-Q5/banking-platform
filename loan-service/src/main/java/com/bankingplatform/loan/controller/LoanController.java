
package com.bankingplatform.loan.controller;
import com.bankingplatform.loan.dto.LoanDtos.*;
import com.bankingplatform.loan.exception.NotFoundException;
import com.bankingplatform.loan.model.Loan;
import com.bankingplatform.loan.messaging.LoanEventPublisher;
import com.bankingplatform.loan.repository.LoanRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.math.MathContext; import java.math.RoundingMode;
import java.util.List; import java.util.Locale; import java.util.UUID;

@RestController @RequestMapping("/api/loans")
public class LoanController {
    private static final Logger log = LoggerFactory.getLogger(LoanController.class);
    private final LoanRepository repository;
    private final LoanEventPublisher eventPublisher;
    public LoanController(LoanRepository repository, LoanEventPublisher eventPublisher){
        this.repository=repository;
        this.eventPublisher=eventPublisher;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse apply(@Valid @RequestBody ApplyLoanRequest request){
        Loan loan = new Loan();
        loan.setCustomerId(request.customerId());
        loan.setProductCode(request.productCode());
        loan.setPrincipal(request.principal());
        loan.setInterestRate(request.interestRate());
        loan.setTermMonths(request.termMonths());
        loan.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        loan.setPurpose(request.purpose());
        loan.setMonthlyPayment(amortize(request.principal(), request.interestRate(), request.termMonths()));
        loan.setOutstandingBalance(request.principal());
        loan.setStatus(Loan.Status.APPLIED);
        Loan saved = repository.save(loan);
        log.info("loan_applied id={} customerId={} principal={}", saved.getId(), saved.getCustomerId(), saved.getPrincipal());
        eventPublisher.applied(saved);
        return LoanResponse.from(saved);
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable UUID id){
        return LoanResponse.from(repository.findById(id).orElseThrow(() -> new NotFoundException("Loan not found: "+id)));
    }

    @GetMapping
    public List<LoanResponse> list(@RequestParam(required=false) UUID customerId, @RequestParam(required=false) String status){
        if (customerId != null) return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(LoanResponse::from).toList();
        if (status != null) return repository.findByStatus(Loan.Status.valueOf(status.toUpperCase(Locale.ROOT))).stream().map(LoanResponse::from).toList();
        return repository.findAll().stream().map(LoanResponse::from).toList();
    }

    @PostMapping("/{id}/decision")
    public LoanResponse decide(@PathVariable UUID id, @Valid @RequestBody LoanDecisionRequest request){
        Loan loan = repository.findById(id).orElseThrow(() -> new NotFoundException("Loan not found: "+id));
        String previous = loan.getStatus().name();
        String decision = request.decision().toUpperCase(Locale.ROOT);
        switch (decision) {
            case "APPROVE" -> { loan.setStatus(Loan.Status.APPROVED); }
            case "ACTIVATE" -> { loan.setStatus(Loan.Status.ACTIVE); }
            case "REJECT" -> { loan.setStatus(Loan.Status.REJECTED); }
            case "REVIEW" -> { loan.setStatus(Loan.Status.UNDER_REVIEW); }
            default -> throw new IllegalArgumentException("decision must be APPROVE|ACTIVATE|REJECT|REVIEW");
        }
        Loan saved = repository.save(loan);
        log.info("loan_decision id={} status={}", saved.getId(), saved.getStatus());
        eventPublisher.statusChanged(saved, previous);
        return LoanResponse.from(saved);
    }

    private BigDecimal amortize(BigDecimal principal, BigDecimal annualRatePct, int months){
        if (months <= 0) return principal;
        BigDecimal monthlyRate = annualRatePct.divide(BigDecimal.valueOf(1200), MathContext.DECIMAL64);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        // M = P * r(1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow = onePlusR.pow(months, MathContext.DECIMAL64);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(pow);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
