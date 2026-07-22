package com.bankingplatform.customer.service;

import com.bankingplatform.customer.dto.CustomerDtos.SupportPinVerifyResponse;
import com.bankingplatform.customer.email.SupportPinMailer;
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
import java.util.concurrent.ThreadLocalRandom;

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
    private final SupportPinMailer mailer;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SupportPinService(CustomerRepository repository, SupportPinMailer mailer) {
        this.repository = repository;
        this.mailer = mailer;
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

    /** Generate a random 4-digit PIN, store its hash, and email it for tracing. */
    @Transactional
    public String issueAndEmail(Customer customer) {
        String pin = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        setPin(customer.getId(), pin);
        // Reload so mailer sees current fields after save
        Customer fresh = find(customer.getId());
        mailer.sendPinIssued(fresh, pin);
        return pin;
    }

    /** One-way bcrypt encode — use when writing a hash into SQL by hand. */
    public String hash(String pin) {
        requireFourDigits(pin);
        return encoder.encode(pin);
    }

    /** Constant-time-ish check of a plaintext PIN against an existing bcrypt hash. */
    public boolean matches(String pin, String hash) {
        requireFourDigits(pin);
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash is required");
        }
        return encoder.matches(pin, hash);
    }

    /**
     * Recover a 4-digit PIN from its bcrypt hash by exhaustive search (0000–9999).
     * Bcrypt is not reversible; this only works because the PIN space is tiny.
     * Ops-only — never expose via public docs.
     */
    public String recover(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash is required");
        }
        for (int i = 0; i < 10000; i++) {
            String candidate = String.format("%04d", i);
            if (encoder.matches(candidate, hash)) {
                log.warn("support_pin_recovered — ops recovery used");
                return candidate;
            }
        }
        throw new IllegalArgumentException("No 4-digit PIN matches this hash (corrupt or not a support PIN hash)");
    }

    public PinStatus status(UUID customerId) {
        Customer customer = find(customerId);
        Instant lockedUntil = customer.getSupportPinLockedUntil();
        boolean locked = lockedUntil != null && lockedUntil.isAfter(Instant.now());
        return new PinStatus(
            customer.getId(),
            customer.getEmail(),
            customer.getSupportPinHash() != null,
            customer.getSupportPinHash(),
            customer.getSupportPinSetAt(),
            customer.getSupportPinFailedAttempts(),
            locked,
            lockedUntil
        );
    }

    @Transactional
    public void unlock(UUID customerId) {
        Customer customer = find(customerId);
        customer.setSupportPinFailedAttempts(0);
        customer.setSupportPinLockedUntil(null);
        repository.save(customer);
        log.info("support_pin_unlocked customerId={}", customerId);
    }

    private void requireFourDigits(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits");
        }
    }

    private Customer find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found: " + id));
    }

    public record PinStatus(
        UUID customerId,
        String email,
        boolean pinSet,
        String hash,
        Instant setAt,
        int failedAttempts,
        boolean locked,
        Instant lockedUntil
    ) {}
}
