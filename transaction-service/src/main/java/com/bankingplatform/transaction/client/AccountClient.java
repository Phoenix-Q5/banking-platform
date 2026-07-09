package com.bankingplatform.transaction.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final WebClient webClient;

    public AccountClient(WebClient accountServiceWebClient) {
        this.webClient = accountServiceWebClient;
    }

    public AccountSnapshot getAccount(UUID accountId) {
        try {
            return webClient.get()
                .uri("/api/accounts/internal/{id}", accountId)
                .retrieve()
                .bodyToMono(AccountSnapshot.class)
                .block(java.time.Duration.ofSeconds(3));
        } catch (Exception ex) {
            log.warn("account_lookup_failed accountId={} reason={}", accountId, ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountSnapshot(UUID id, String accountNumber, UUID customerId, java.math.BigDecimal balance,
                                  String currency, String status) {
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "debitFallback")
    @TimeLimiter(name = "accountService")
    @Retry(name = "accountService")
    public CompletableFuture<Void> debitAccount(UUID accountId, UUID transactionId, BigDecimal amount) {
        return webClient.post()
            .uri("/api/accounts/{id}/debit", accountId)
            .bodyValue(Map.of("transactionId", transactionId, "amount", amount))
            .retrieve()
            .toBodilessEntity()
            .then()
            .toFuture();
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "creditFallback")
    @TimeLimiter(name = "accountService")
    @Retry(name = "accountService")
    public CompletableFuture<Void> creditAccount(UUID accountId, UUID transactionId, BigDecimal amount) {
        return webClient.post()
            .uri("/api/accounts/{id}/credit", accountId)
            .bodyValue(Map.of("transactionId", transactionId, "amount", amount))
            .retrieve()
            .toBodilessEntity()
            .then()
            .toFuture();
    }

    private CompletableFuture<Void> debitFallback(UUID accountId, UUID transactionId, BigDecimal amount, Throwable t) {
        log.error("debit_call_failed accountId={} txnId={} reason={}", accountId, transactionId, t.toString());
        return CompletableFuture.failedFuture(new DownstreamUnavailableException("account-service debit failed: " + rootMessage(t), t));
    }

    private CompletableFuture<Void> creditFallback(UUID accountId, UUID transactionId, BigDecimal amount, Throwable t) {
        log.error("credit_call_failed accountId={} txnId={} reason={}", accountId, transactionId, t.toString());
        return CompletableFuture.failedFuture(new DownstreamUnavailableException("account-service credit failed: " + rootMessage(t), t));
    }

    private String rootMessage(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode() + " " + wcre.getResponseBodyAsString();
        }
        return t.getMessage();
    }

    public static class DownstreamUnavailableException extends RuntimeException {
        public DownstreamUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
