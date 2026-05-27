package com.eventledger.account.service;

import com.eventledger.account.model.AccountEntity;
import com.eventledger.account.model.TransactionEntity;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.dto.EventRequest;
import com.eventledger.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private AccountService accountService;

    @Test
    void shouldCreateAccountOnFirstTransaction() {
        TransactionRequest request = createTransactionRequest("evt-001", "CREDIT", BigDecimal.valueOf(100));
        when(transactionRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        when(accountRepository.findById("acct-123")).thenReturn(Optional.empty());
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAndFlush(any())).thenReturn(null);

        // The service creates and saves the account; buildAccountResponse re-fetches it
        var account = new AccountEntity("acct-123");
        when(accountRepository.findById("acct-123")).thenReturn(Optional.of(account));

        var result = accountService.applyTransaction(request);
        assertEquals("acct-123", result.getAccountId());
        assertEquals(BigDecimal.valueOf(100), result.getBalance());
    }

    @Test
    void shouldBeIdempotentForDuplicateEventId() {
        TransactionRequest request = createTransactionRequest("evt-001", "CREDIT", BigDecimal.valueOf(100));
        var account = new AccountEntity("acct-123");
        account.applyCredit(BigDecimal.valueOf(100));
        var existingTxn = new TransactionEntity();
        existingTxn.setAccountId("acct-123");

        when(transactionRepository.findByEventId("evt-001")).thenReturn(Optional.of(existingTxn));
        when(accountRepository.findById("acct-123")).thenReturn(Optional.of(account));

        var result = accountService.applyTransaction(request);
        assertEquals(BigDecimal.valueOf(100), result.getBalance());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCalculateCorrectBalanceWithCreditAndDebit() {
        // Credit 200 — service applies credit on the same account object
        TransactionRequest creditReq = createTransactionRequest("evt-001", "CREDIT", BigDecimal.valueOf(200));
        var account = new AccountEntity("acct-123");
        when(accountRepository.findById("acct-123")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAndFlush(any())).thenReturn(null);
        when(transactionRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        accountService.applyTransaction(creditReq);

        // Debit 50 — service applies debit on the same account object
        TransactionRequest debitReq = createTransactionRequest("evt-002", "DEBIT", BigDecimal.valueOf(50));
        when(transactionRepository.findByEventId("evt-002")).thenReturn(Optional.empty());
        accountService.applyTransaction(debitReq);

        assertEquals(BigDecimal.valueOf(150), account.getBalance());
    }

    @Test
    void shouldThrowNotFoundExceptionForUnknownAccount() {
        when(accountRepository.findById("unknown")).thenReturn(Optional.empty());
        assertThrows(AccountService.AccountNotFoundException.class,
                () -> accountService.getBalance("unknown"));
    }

    private TransactionRequest createTransactionRequest(String eventId, String type, BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        req.setEventId(eventId);
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.valueOf(type));
        req.setAmount(amount);
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());
        return req;
    }
}
