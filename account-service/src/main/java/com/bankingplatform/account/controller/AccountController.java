package com.bankingplatform.account.controller;

import com.bankingplatform.account.dto.AccountResponse;
import com.bankingplatform.account.dto.BalanceAdjustmentRequest;
import com.bankingplatform.account.dto.CreateAccountRequest;
import com.bankingplatform.account.dto.DepositRequest;
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
                // Every newly opened account awaits an admin decision before it can transact.
                .status(Account.AccountStatus.PENDING_APPROVAL)
                .build();

        Account saved = accountRepository.save(account);
        log.info("account_created accountId={} customerId={} status={}", saved.getId(), saved.getCustomerId(), saved.getStatus());
        eventPublisher.opened(saved);
        return AccountResponse.from(saved);
    }

    /** Admin decision on a newly opened account: APPROVE activates it, REJECT closes it. */
    @PostMapping("/{id}/decision")
    @Transactional
    public AccountResponse decide(@PathVariable("id") UUID id, @RequestBody DecisionRequest request) {
        Account account = findOrThrow(id);
        if (account.getStatus() != Account.AccountStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Account is not pending approval (status: " + account.getStatus() + ")");
        }
        String action = request.action() == null ? "" : request.action().toUpperCase();
        switch (action) {
            case "APPROVE" -> account.setStatus(Account.AccountStatus.ACTIVE);
            case "REJECT" -> account.setStatus(Account.AccountStatus.CLOSED);
            default -> throw new IllegalArgumentException("Unknown action: " + request.action() + " (expected APPROVE or REJECT)");
        }
        Account saved = accountRepository.save(account);
        log.info("account_decision accountId={} action={} status={}", id, action, saved.getStatus());
        return AccountResponse.from(saved);
    }

    /** Temporary freeze placed by admin/support; blocks debits, deposits, and transfers out. */
    @PostMapping("/{id}/freeze")
    @Transactional
    public AccountResponse freeze(@PathVariable("id") UUID id) {
        Account account = findOrThrow(id);
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active accounts can be frozen (status: " + account.getStatus() + ")");
        }
        account.setStatus(Account.AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);
        log.info("account_frozen accountId={}", id);
        return AccountResponse.from(saved);
    }

    @PostMapping("/{id}/unfreeze")
    @Transactional
    public AccountResponse unfreeze(@PathVariable("id") UUID id) {
        Account account = findOrThrow(id);
        if (account.getStatus() != Account.AccountStatus.FROZEN) {
            throw new IllegalArgumentException("Account is not frozen (status: " + account.getStatus() + ")");
        }
        account.setStatus(Account.AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        log.info("account_unfrozen accountId={}", id);
        return AccountResponse.from(saved);
    }

    public record DecisionRequest(String action) {}

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable("id") UUID id) {
        return AccountResponse.from(findOrThrow(id));
    }

    /** Internal S2S lookup used by transaction-service (private network). */
    @GetMapping("/internal/{id}")
    public AccountResponse getAccountInternal(@PathVariable("id") UUID id) {
        return AccountResponse.from(findOrThrow(id));
    }

    @GetMapping
    public List<AccountResponse> list(
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "status", required = false) String status) {
        if (customerId != null) {
            return accountRepository.findByCustomerId(customerId).stream()
                .map(AccountResponse::from)
                .toList();
        }
        if (status != null && !status.isBlank()) {
            Account.AccountStatus parsed = Account.AccountStatus.valueOf(status.toUpperCase());
            return accountRepository.findByStatusOrderByCreatedAtAsc(parsed).stream()
                .map(AccountResponse::from)
                .toList();
        }
        throw new IllegalArgumentException("customerId or status query parameter is required");
    }

    @PostMapping("/{id}/debit")
    @Transactional
    public ResponseEntity<AccountResponse> debit(@PathVariable("id") UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            log.warn("debit_rejected_account_status accountId={} status={}", id, account.getStatus());
            throw new IllegalArgumentException("Account is not active (status: " + account.getStatus() + ")");
        }
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
    public ResponseEntity<AccountResponse> credit(@PathVariable("id") UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.setBalance(account.getBalance().add(request.amount()));
        Account saved = accountRepository.save(account);
        log.info("account_credited accountId={} txnId={} amount={} newBalance={}", id, request.transactionId(), request.amount(), saved.getBalance());
        return ResponseEntity.ok(AccountResponse.from(saved));
    }

    /** Customer-facing demo funding (authenticated via gateway JWT). */
    @PostMapping("/{id}/deposit")
    @Transactional
    public AccountResponse deposit(@PathVariable("id") UUID id, @Valid @RequestBody DepositRequest request) {
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
        account.setBalance(account.getBalance().add(request.amount()));
        Account saved = accountRepository.save(account);
        log.info("account_deposit accountId={} amount={} memo={} newBalance={}",
            id, request.amount(), request.memo(), saved.getBalance());
        return AccountResponse.from(saved);
    }

    private Account findOrThrow(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private String generateAccountNumber() {
        return "ACC" + System.currentTimeMillis() + (int) (Math.random() * 900 + 100);
    }
}
