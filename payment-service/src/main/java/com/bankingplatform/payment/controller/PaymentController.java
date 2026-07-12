
package com.bankingplatform.payment.controller;

import com.bankingplatform.payment.dto.PaymentDtos.*;
import com.bankingplatform.payment.exception.NotFoundException;
import com.bankingplatform.payment.model.Beneficiary;
import com.bankingplatform.payment.model.Payment;
import com.bankingplatform.payment.repository.BeneficiaryRepository;
import com.bankingplatform.payment.messaging.PaymentEventPublisher;
import com.bankingplatform.payment.repository.PaymentRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentRepository paymentRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PaymentEventPublisher eventPublisher;

    public PaymentController(PaymentRepository paymentRepository, BeneficiaryRepository beneficiaryRepository, PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/beneficiaries")
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse createBeneficiary(@Valid @RequestBody CreateBeneficiaryRequest request) {
        Beneficiary b = new Beneficiary();
        b.setCustomerId(request.customerId());
        b.setNickname(request.nickname());
        b.setAccountNumber(request.accountNumber());
        b.setRoutingNumber(request.routingNumber());
        b.setBankName(request.bankName());
        if (request.currency() != null) b.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        Beneficiary saved = beneficiaryRepository.save(b);
        log.info("beneficiary_created id={} customerId={}", saved.getId(), saved.getCustomerId());
        return BeneficiaryResponse.from(saved);
    }

    @GetMapping("/beneficiaries")
    public List<BeneficiaryResponse> listBeneficiaries(@RequestParam("customerId") UUID customerId) {
        return beneficiaryRepository.findByCustomerId(customerId).stream().map(BeneficiaryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        if (request.beneficiaryId() != null) {
            beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new NotFoundException("Beneficiary not found: " + request.beneficiaryId()));
        }
        Payment payment = new Payment();
        payment.setCustomerId(request.customerId());
        payment.setFromAccountId(request.fromAccountId());
        payment.setBeneficiaryId(request.beneficiaryId());
        payment.setPaymentType(Payment.PaymentType.valueOf(request.paymentType().toUpperCase(Locale.ROOT)));
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        payment.setReference(request.reference());
        payment.setDescription(request.description());
        payment.setScheduledFor(request.scheduledFor());
        payment.setStatus(request.scheduledFor() != null ? Payment.Status.SCHEDULED : Payment.Status.PENDING);
        // Prototype: mark non-scheduled payments completed immediately (rail simulation).
        if (payment.getStatus() == Payment.Status.PENDING) {
            payment.setStatus(Payment.Status.COMPLETED);
        }
        Payment saved = paymentRepository.save(payment);
        log.info("payment_created id={} type={} amount={} status={}", saved.getId(), saved.getPaymentType(), saved.getAmount(), saved.getStatus());
        if (saved.getStatus() == Payment.Status.COMPLETED) {
            eventPublisher.paymentCompleted(saved);
        }
        return PaymentResponse.from(saved);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable("id") UUID id) {
        return PaymentResponse.from(paymentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Payment not found: " + id)));
    }

    @GetMapping
    public List<PaymentResponse> list(@RequestParam(name = "customerId", required = false) UUID customerId,
                                      @RequestParam(name = "accountId", required = false) UUID accountId) {
        if (customerId != null) {
            return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(PaymentResponse::from).toList();
        }
        if (accountId != null) {
            return paymentRepository.findByFromAccountIdOrderByCreatedAtDesc(accountId).stream().map(PaymentResponse::from).toList();
        }
        return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable("id") UUID id) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Payment not found: " + id));
        if (payment.getStatus() != Payment.Status.SCHEDULED && payment.getStatus() != Payment.Status.PENDING) {
            throw new IllegalArgumentException("Only pending/scheduled payments can be cancelled");
        }
        payment.setStatus(Payment.Status.CANCELLED);
        return PaymentResponse.from(paymentRepository.save(payment));
    }
}
