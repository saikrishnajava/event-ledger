package com.eventledger.account.service;

import com.eventledger.account.model.AccountEntity;
import com.eventledger.account.model.TransactionEntity;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.TransactionRequest;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    private static final int MAX_RETRIES = 3;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public AccountResponse applyTransaction(TransactionRequest request) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return doApplyTransaction(request);
            } catch (OptimisticLockException e) {
                if (attempt == MAX_RETRIES - 1) {
                    log.error("Optimistic lock retry exhausted for accountId={}", request.getAccountId());
                    throw new AccountConflictException("Account updated concurrently, please retry", e);
                }
                long backoff = (long) (Math.random() * 50 + 10);
                log.warn("Optimistic lock conflict, retrying in {}ms (attempt {}/{})", backoff, attempt + 1, MAX_RETRIES);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new AccountConflictException("Account updated concurrently, please retry");
    }

    @Transactional
    protected AccountResponse doApplyTransaction(TransactionRequest request) {
        log.info("Applying transaction: eventId={}, accountId={}, type={}, amount={}",
                request.getEventId(), request.getAccountId(), request.getType(), request.getAmount());

        var existingTxn = transactionRepository.findByEventId(request.getEventId());
        if (existingTxn.isPresent()) {
            log.info("Duplicate transaction detected: eventId={}, returning existing state", request.getEventId());
            return buildAccountResponse(existingTxn.get().getAccountId());
        }

        AccountEntity account = accountRepository.findById(request.getAccountId())
                .orElseGet(() -> {
                    log.info("Creating new account: accountId={}", request.getAccountId());
                    AccountEntity newAccount = new AccountEntity(request.getAccountId());
                    newAccount.setCurrency(request.getCurrency());
                    return accountRepository.save(newAccount);
                });

        // Validate currency consistency
        if (account.getCurrency() != null && !account.getCurrency().equals(request.getCurrency())) {
            throw new CurrencyMismatchException(account.getCurrency(), request.getCurrency());
        }

        TransactionEntity txn = new TransactionEntity();
        txn.setEventId(request.getEventId());
        txn.setAccountId(request.getAccountId());
        txn.setType(request.getType());
        txn.setAmount(request.getAmount());
        txn.setEventTimestamp(request.getEventTimestamp());

        try {
            transactionRepository.saveAndFlush(txn);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                log.warn("Race condition on duplicate eventId={}, returning existing state", request.getEventId());
                return buildAccountResponse(request.getAccountId());
            }
            log.error("Unexpected constraint violation for eventId={}: {}", request.getEventId(), e.getMessage());
            throw e;
        }

        if (request.getType() == com.eventledger.dto.EventRequest.EventType.CREDIT) {
            account.applyCredit(request.getAmount());
        } else {
            account.applyDebit(request.getAmount());
        }
        accountRepository.save(account);

        log.info("Transaction applied successfully: eventId={}, newBalance={}", request.getEventId(), account.getBalance());
        return buildAccountResponse(account.getAccountId());
    }

    public AccountResponse getAccount(String accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return buildAccountResponse(account.getAccountId());
    }

    public AccountResponse getBalance(String accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        AccountResponse response = new AccountResponse();
        response.setAccountId(account.getAccountId());
        response.setBalance(account.getBalance());
        response.setUpdatedAt(account.getUpdatedAt());
        return response;
    }

    private AccountResponse buildAccountResponse(String accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        List<TransactionEntity> recentTxns = transactionRepository
                .findTop20ByAccountIdOrderByAppliedAtDesc(accountId);

        AccountResponse response = new AccountResponse();
        response.setAccountId(account.getAccountId());
        response.setBalance(account.getBalance());
        response.setUpdatedAt(account.getUpdatedAt());
        response.setRecentTransactions(recentTxns.stream().map(txn -> {
            AccountResponse.TransactionSummary summary = new AccountResponse.TransactionSummary();
            summary.setEventId(txn.getEventId());
            summary.setType(txn.getType().name());
            summary.setAmount(txn.getAmount());
            summary.setEventTimestamp(txn.getEventTimestamp());
            summary.setAppliedAt(txn.getAppliedAt());
            return summary;
        }).collect(Collectors.toList()));
        return response;
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String accountId) {
            super("Account not found: " + accountId);
        }
    }

    public static class AccountConflictException extends RuntimeException {
        public AccountConflictException(String message) {
            super(message);
        }
        public AccountConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class CurrencyMismatchException extends RuntimeException {
        public CurrencyMismatchException(String expected, String actual) {
            super("Currency mismatch: expected " + expected + " but got " + actual);
        }
    }
}
