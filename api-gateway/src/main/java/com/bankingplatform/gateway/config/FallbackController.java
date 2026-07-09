package com.bankingplatform.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/account-service")
    @PostMapping("/account-service")
    @PutMapping("/account-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> accountServiceFallback() {
        return degraded("account-service");
    }

    @GetMapping("/transaction-service")
    @PostMapping("/transaction-service")
    @PutMapping("/transaction-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> transactionServiceFallback() {
        return degraded("transaction-service");
    }

    @GetMapping("/customer-service")
    @PostMapping("/customer-service")
    @PutMapping("/customer-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> customerServiceFallback() {
        return degraded("customer-service");
    }

    @GetMapping("/payment-service")
    @PostMapping("/payment-service")
    @PutMapping("/payment-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> paymentServiceFallback() {
        return degraded("payment-service");
    }

    @GetMapping("/card-service")
    @PostMapping("/card-service")
    @PutMapping("/card-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> cardServiceFallback() {
        return degraded("card-service");
    }

    @GetMapping("/notification-service")
    @PostMapping("/notification-service")
    @PutMapping("/notification-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> notificationServiceFallback() {
        return degraded("notification-service");
    }

    @GetMapping("/audit-service")
    @PostMapping("/audit-service")
    @PutMapping("/audit-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> auditServiceFallback() {
        return degraded("audit-service");
    }

    @GetMapping("/loan-service")
    @PostMapping("/loan-service")
    @PutMapping("/loan-service")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> loanServiceFallback() {
        return degraded("loan-service");
    }

    private Mono<Map<String, Object>> degraded(String downstream) {
        return Mono.just(Map.of(
            "status", "DEGRADED",
            "downstream", downstream,
            "message", downstream + " is currently unavailable. Please retry shortly.",
            "timestamp", Instant.now().toString()
        ));
    }
}
