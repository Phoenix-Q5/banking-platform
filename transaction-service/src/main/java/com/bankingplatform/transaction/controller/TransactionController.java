package com.bankingplatform.transaction.controller;

import com.bankingplatform.transaction.client.AccountClient;
import com.bankingplatform.transaction.dto.TransactionResponse;
import com.bankingplatform.transaction.dto.TransferRequest;
import com.bankingplatform.transaction.exception.TransactionNotFoundException;
import com.bankingplatform.transaction.messaging.TransferEventPublisher;
import com.bankingplatform.transaction.model.Transaction;
import com.bankingplatform.transaction.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final TransferEventPublisher eventPublisher;
    private final BigDecimal approvalThreshold;

    public TransactionController(TransactionRepository transactionRepository,
                                 AccountClient accountClient,
                                 TransferEventPublisher eventPublisher,
                                 @Value("${transfers.approval-threshold:10000}") BigDecimal approvalThreshold) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.eventPublisher = eventPublisher;
        this.approvalThreshold = approvalThreshold;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        Transaction txn = new Transaction();
        txn.setFromAccountId(request.fromAccountId());
        txn.setToAccountId(request.toAccountId());
        txn.setAmount(request.amount());
        txn.setCurrency(request.currency());

        // Transfers at or above the threshold are held for an admin decision.
        if (request.amount().compareTo(approvalThreshold) >= 0) {
            txn.setStatus(Transaction.TransactionStatus.PENDING_APPROVAL);
            Transaction saved = transactionRepository.save(txn);
            log.info("transfer_held_for_approval txnId={} amount={} threshold={}",
                saved.getId(), request.amount(), approvalThreshold);
            return TransactionResponse.from(saved);
        }

        txn.setStatus(Transaction.TransactionStatus.PENDING);
        txn = transactionRepository.save(txn);
        return execute(txn);
    }

    /** Admin decision on a held transfer: APPROVE executes it, REJECT fails it. */
    @PostMapping("/{id}/decision")
    public TransactionResponse decide(@PathVariable("id") UUID id, @RequestBody DecisionRequest request) {
        Transaction txn = transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
        if (txn.getStatus() != Transaction.TransactionStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Transfer is not pending approval (status: " + txn.getStatus() + ")");
        }
        String action = request.action() == null ? "" : request.action().toUpperCase();
        switch (action) {
            case "APPROVE" -> {
                txn.setStatus(Transaction.TransactionStatus.PENDING);
                txn = transactionRepository.save(txn);
                log.info("transfer_approved txnId={}", id);
                return execute(txn);
            }
            case "REJECT" -> {
                UUID fromCustomerId = customerIdOf(txn.getFromAccountId());
                log.info("transfer_rejected txnId={}", id);
                return fail(txn, fromCustomerId, "Rejected by admin");
            }
            default -> throw new IllegalArgumentException("Unknown action: " + request.action() + " (expected APPROVE or REJECT)");
        }
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable("id") UUID id) {
        Transaction txn = transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
        return TransactionResponse.from(txn);
    }

    @GetMapping
    public List<TransactionResponse> list(
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "status", required = false) String status) {
        if (accountId != null) {
            return transactionRepository.findByFromAccountIdOrToAccountId(accountId, accountId).stream()
                .map(TransactionResponse::from)
                .toList();
        }
        if (status != null && !status.isBlank()) {
            Transaction.TransactionStatus parsed = Transaction.TransactionStatus.valueOf(status.toUpperCase());
            return transactionRepository.findByStatusOrderByCreatedAtAsc(parsed).stream()
                .map(TransactionResponse::from)
                .toList();
        }
        throw new IllegalArgumentException("accountId or status query parameter is required");
    }

    /** Runs the debit/credit saga for a transfer already persisted in PENDING state. */
    private TransactionResponse execute(Transaction txn) {
        UUID txnId = txn.getId();
        AccountClient.AccountSnapshot fromAccount = accountClient.getAccount(txn.getFromAccountId());
        AccountClient.AccountSnapshot toAccount = accountClient.getAccount(txn.getToAccountId());
        UUID fromCustomerId = fromAccount == null ? null : fromAccount.customerId();
        UUID toCustomerId = toAccount == null ? null : toAccount.customerId();

        try {
            accountClient.debitAccount(txn.getFromAccountId(), txnId, txn.getAmount()).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return fail(txn, fromCustomerId, "Debit failed: " + rootCause(e));
        }

        try {
            accountClient.creditAccount(txn.getToAccountId(), txnId, txn.getAmount()).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("credit_failed_compensating txnId={} reason={}", txnId, e.getMessage());
            try {
                accountClient.creditAccount(txn.getFromAccountId(), txnId, txn.getAmount()).get();
            } catch (InterruptedException | ExecutionException compensationError) {
                Thread.currentThread().interrupt();
                log.error("compensation_failed txnId={} - MANUAL INTERVENTION REQUIRED reason={}", txnId, compensationError.getMessage());
                return fail(txn, fromCustomerId, "Credit failed AND compensation failed — funds may be stuck. Manual reconciliation required.");
            }
            return fail(txn, fromCustomerId, "Credit failed, debit was reversed: " + rootCause(e));
        }

        txn.setStatus(Transaction.TransactionStatus.COMPLETED);
        Transaction saved = transactionRepository.save(txn);
        log.info("transfer_completed txnId={} from={} to={} amount={}", txnId, txn.getFromAccountId(), txn.getToAccountId(), txn.getAmount());
        eventPublisher.transferCompleted(saved, fromCustomerId, toCustomerId);
        return TransactionResponse.from(saved);
    }

    private UUID customerIdOf(UUID accountId) {
        AccountClient.AccountSnapshot snapshot = accountClient.getAccount(accountId);
        return snapshot == null ? null : snapshot.customerId();
    }

    private TransactionResponse fail(Transaction txn, UUID fromCustomerId, String reason) {
        txn.setStatus(Transaction.TransactionStatus.FAILED);
        txn.setFailureReason(reason);
        Transaction saved = transactionRepository.save(txn);
        log.error("transfer_failed txnId={} reason={}", txn.getId(), reason);
        eventPublisher.transferFailed(saved, fromCustomerId, reason);
        return TransactionResponse.from(saved);
    }

    private String rootCause(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage();
    }

    public record DecisionRequest(String action) {}
}
