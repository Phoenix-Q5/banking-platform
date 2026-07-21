package com.bankingplatform.customer.service;

import com.bankingplatform.customer.dto.CustomerDtos.SupportPinVerifyResponse;
import com.bankingplatform.customer.exception.NotFoundException;
import com.bankingplatform.customer.model.Customer;
import com.bankingplatform.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Manages the customer's secret 4-digit support PIN. The PIN is stored only as
 * a bcrypt hash and verification is rate-limited: after too many failures the
 * PIN is temporarily locked to compensate for the low entropy of 4 digits.
 */
@Service
public class SupportPinService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(SupportPinService.class);
    private final CustomerRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SupportPinService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void setPin(UUID customerId, String pin) {
        Customer customer = find(customerId);
        customer.setSupportPinHash(encoder.encode(pin));
        customer.setSupportPinSetAt(Instant.now());
        customer.setSupportPinFailedAttempts(0);
        customer.setSupportPinLockedUntil(null);
        repository.save(customer);
        log.info("support_pin_set customerId={}", customerId);
    }

    @Transactional
    public SupportPinVerifyResponse verify(UUID customerId, String pin) {
        Customer customer = find(customerId);
        if (customer.getSupportPinHash() == null) {
            throw new IllegalArgumentException("Customer has no support PIN on file");
        }
        Instant lockedUntil = customer.getSupportPinLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            log.warn("support_pin_verify_locked customerId={}", customerId);
            return new SupportPinVerifyResponse(false, true, 0, lockedUntil);
        }
        if (encoder.matches(pin, customer.getSupportPinHash())) {
            customer.setSupportPinFailedAttempts(0);
            customer.setSupportPinLockedUntil(null);
            repository.save(customer);
            log.info("support_pin_verify_success customerId={}", customerId);
            return new SupportPinVerifyResponse(true, false, null, null);
        }
        int failures = customer.getSupportPinFailedAttempts() + 1;
        customer.setSupportPinFailedAttempts(failures);
        boolean locked = failures >= MAX_FAILED_ATTEMPTS;
        if (locked) {
            customer.setSupportPinLockedUntil(Instant.now().plus(LOCKOUT));
        }
        repository.save(customer);
        log.warn("support_pin_verify_failure customerId={} failedAttempts={} locked={}", customerId, failures, locked);
        return new SupportPinVerifyResponse(
            false, locked, Math.max(0, MAX_FAILED_ATTEMPTS - failures), customer.getSupportPinLockedUntil()
        );
    }

    private Customer find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found: " + id));
    }
}
