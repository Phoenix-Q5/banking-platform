package com.bankingplatform.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Circuit-breaker fallbacks. Must accept every HTTP method the gateway may
 * forward — stacking {@code @GetMapping}/{@code @PostMapping} on one method is
 * ignored by WebFlux (only the first annotation sticks), which produced
 * confusing {@code 405 Method Not Allowed} when customer registration fell
 * through to this controller.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final RequestMethod[] ALL = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE
    };

    @RequestMapping(value = "/account-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> accountServiceFallback() {
        return degraded("account-service");
    }

    @RequestMapping(value = "/transaction-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> transactionServiceFallback() {
        return degraded("transaction-service");
    }

    @RequestMapping(value = "/customer-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> customerServiceFallback() {
        return degraded("customer-service");
    }

    @RequestMapping(value = "/payment-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> paymentServiceFallback() {
        return degraded("payment-service");
    }

    @RequestMapping(value = "/card-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> cardServiceFallback() {
        return degraded("card-service");
    }

    @RequestMapping(value = "/notification-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> notificationServiceFallback() {
        return degraded("notification-service");
    }

    @RequestMapping(value = "/audit-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> auditServiceFallback() {
        return degraded("audit-service");
    }

    @RequestMapping(value = "/loan-service", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
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
