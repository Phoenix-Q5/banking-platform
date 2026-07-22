package com.bankingplatform.customer.controller;

import com.bankingplatform.customer.service.SupportPinService;
import com.bankingplatform.customer.service.SupportPinService.PinStatus;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Ops-only support-PIN tools. Hidden from Swagger — bcrypt cannot be decrypted;
 * {@code /recover} brute-forces the 4-digit space when a hash must be reversed
 * for incident recovery.
 */
@Hidden
@RestController
@RequestMapping("/api/customers/management/support-pin")
public class SupportPinManagementController {

    private final SupportPinService supportPinService;

    public SupportPinManagementController(SupportPinService supportPinService) {
        this.supportPinService = supportPinService;
    }

    /** Hash (one-way encrypt) a plaintext PIN for manual SQL / seed fixes. */
    @PostMapping("/hash")
    public Map<String, String> hash(@Valid @RequestBody PinBody body) {
        return Map.of("hash", supportPinService.hash(body.pin()));
    }

    /**
     * Recover the plaintext 4-digit PIN from a bcrypt hash (exhaustive 0000–9999).
     * This is the closest equivalent to "decrypt" for support PINs.
     */
    @PostMapping("/recover")
    public Map<String, String> recover(@Valid @RequestBody HashBody body) {
        return Map.of("pin", supportPinService.recover(body.hash()));
    }

    /** Check whether a plaintext PIN matches a given bcrypt hash. */
    @PostMapping("/check")
    public Map<String, Object> check(@Valid @RequestBody CheckBody body) {
        return Map.of("matches", supportPinService.matches(body.pin(), body.hash()));
    }

    /** Inspect a customer's PIN state (includes hash for SQL copy; never the plaintext). */
    @GetMapping("/{customerId}")
    public PinStatus status(@PathVariable("customerId") UUID customerId) {
        return supportPinService.status(customerId);
    }

    /** Recover the plaintext PIN currently on file for a customer. */
    @PostMapping("/{customerId}/recover")
    public Map<String, String> recoverForCustomer(@PathVariable("customerId") UUID customerId) {
        PinStatus status = supportPinService.status(customerId);
        if (!status.pinSet() || status.hash() == null) {
            throw new IllegalArgumentException("Customer has no support PIN on file");
        }
        return Map.of("pin", supportPinService.recover(status.hash()));
    }

    /** Force-set a new PIN and clear lockout (incident recovery). */
    @PostMapping("/{customerId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable("customerId") UUID customerId, @Valid @RequestBody PinBody body) {
        supportPinService.setPin(customerId, body.pin());
    }

    /** Clear failed-attempt lockout without changing the PIN. */
    @PostMapping("/{customerId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable("customerId") UUID customerId) {
        supportPinService.unlock(customerId);
    }

    public record PinBody(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits") String pin
    ) {}

    public record HashBody(@NotBlank String hash) {}

    public record CheckBody(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits") String pin,
        @NotBlank String hash
    ) {}
}
