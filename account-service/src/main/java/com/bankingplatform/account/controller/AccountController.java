package com.bankingplatform.account.controller;

import com.bankingplatform.account.dto.AccountResponse;
import com.bankingplatform.account.dto.BalanceAdjustmentRequest;
import com.bankingplatform.account.dto.CreateAccountRequest;
import com.bankingplatform.account.exception.AccountNotFoundException;
import com.bankingplatform.account.exception.InsufficientFundsException;
import com.bankingplatform.account.model.Account;
import com.bankingplatform.account.messaging.AccountEventPublisher;
import com.bankingplatform.account.repository.AccountRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepository;
    private final AccountEventPublisher eventPublisher;

    public AccountController(AccountRepository accountRepository, AccountEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = Account.builder()
                .accountNumber(generateAccountNumber())
                .customerId(request.customerId())
                .balance(BigDecimal.ZERO)
                .currency(request.currency())
                .status(Account.AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("account_created accountId={} customerId={}", saved.getId(), saved.getCustomerId());
        eventPublisher.opened(saved);
        return AccountResponse.from(saved);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable UUID id) {
        return AccountResponse.from(findOrThrow(id));
    }

    /** Internal S2S lookup used by transaction-service (private network). */
    @GetMapping("/internal/{id}")
    public AccountResponse getAccountInternal(@PathVariable UUID id) {
        return AccountResponse.from(findOrThrow(id));
    }

    @GetMapping
    public List<AccountResponse> listByCustomer(@RequestParam UUID customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
            .map(AccountResponse::from)
            .toList();
    }

    @PostMapping("/{id}/debit")
    @Transactional
    public ResponseEntity<AccountResponse> debit(@PathVariable UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getBalance().compareTo(request.amount()) < 0) {
            log.warn("debit_rejected_insufficient_funds accountId={} txnId={} amount={}", id, request.transactionId(), request.amount());
            throw new InsufficientFundsException(id);
        }
        account.setBalance(account.getBalance().subtract(request.amount()));
        Account saved = accountRepository.save(account);
        log.info("account_debited accountId={} txnId={} amount={} newBalance={}", id, request.transactionId(), request.amount(), saved.getBalance());
        return ResponseEntity.ok(AccountResponse.from(saved));
    }

    @PostMapping("/{id}/credit")
    @Transactional
    public ResponseEntity<AccountResponse> credit(@PathVariable UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.setBalance(account.getBalance().add(request.amount()));
        Account saved = accountRepository.save(account);
        log.info("account_credited accountId={} txnId={} amount={} newBalance={}", id, request.transactionId(), request.amount(), saved.getBalance());
        return ResponseEntity.ok(AccountResponse.from(saved));
    }

    private Account findOrThrow(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private String generateAccountNumber() {
        return "ACC" + System.currentTimeMillis() + (int) (Math.random() * 900 + 100);
    }
}
