package com.eventledger.account.service;

import com.eventledger.account.model.AccountEntity;
import com.eventledger.account.model.TransactionEntity;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse applyTransaction(TransactionRequest request) {
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
                    return accountRepository.save(new AccountEntity(request.getAccountId()));
                });

        TransactionEntity txn = new TransactionEntity();
        txn.setEventId(request.getEventId());
        txn.setAccountId(request.getAccountId());
        txn.setType(request.getType());
        txn.setAmount(request.getAmount());
        txn.setEventTimestamp(request.getEventTimestamp());

        try {
            transactionRepository.saveAndFlush(txn);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for eventId={}, returning existing state", request.getEventId());
            return buildAccountResponse(request.getAccountId());
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
}
