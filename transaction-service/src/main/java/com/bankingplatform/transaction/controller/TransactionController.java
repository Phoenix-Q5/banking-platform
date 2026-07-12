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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    public TransactionController(TransactionRepository transactionRepository,
                                 AccountClient accountClient,
                                 TransferEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        Transaction txn = new Transaction();
        txn.setFromAccountId(request.fromAccountId());
        txn.setToAccountId(request.toAccountId());
        txn.setAmount(request.amount());
        txn.setCurrency(request.currency());
        txn.setStatus(Transaction.TransactionStatus.PENDING);
        txn = transactionRepository.save(txn);
        UUID txnId = txn.getId();

        AccountClient.AccountSnapshot fromAccount = accountClient.getAccount(request.fromAccountId());
        AccountClient.AccountSnapshot toAccount = accountClient.getAccount(request.toAccountId());
        UUID fromCustomerId = fromAccount == null ? null : fromAccount.customerId();
        UUID toCustomerId = toAccount == null ? null : toAccount.customerId();

        try {
            accountClient.debitAccount(request.fromAccountId(), txnId, request.amount()).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return fail(txn, fromCustomerId, "Debit failed: " + rootCause(e));
        }

        try {
            accountClient.creditAccount(request.toAccountId(), txnId, request.amount()).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("credit_failed_compensating txnId={} reason={}", txnId, e.getMessage());
            try {
                accountClient.creditAccount(request.fromAccountId(), txnId, request.amount()).get();
            } catch (InterruptedException | ExecutionException compensationError) {
                Thread.currentThread().interrupt();
                log.error("compensation_failed txnId={} - MANUAL INTERVENTION REQUIRED reason={}", txnId, compensationError.getMessage());
                return fail(txn, fromCustomerId, "Credit failed AND compensation failed — funds may be stuck. Manual reconciliation required.");
            }
            return fail(txn, fromCustomerId, "Credit failed, debit was reversed: " + rootCause(e));
        }

        txn.setStatus(Transaction.TransactionStatus.COMPLETED);
        Transaction saved = transactionRepository.save(txn);
        log.info("transfer_completed txnId={} from={} to={} amount={}", txnId, request.fromAccountId(), request.toAccountId(), request.amount());
        eventPublisher.transferCompleted(saved, fromCustomerId, toCustomerId);
        return TransactionResponse.from(saved);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable("id") UUID id) {
        Transaction txn = transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
        return TransactionResponse.from(txn);
    }

    @GetMapping
    public List<TransactionResponse> listByAccount(@RequestParam("accountId") UUID accountId) {
        return transactionRepository.findByFromAccountIdOrToAccountId(accountId, accountId).stream()
            .map(TransactionResponse::from)
            .toList();
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
}
