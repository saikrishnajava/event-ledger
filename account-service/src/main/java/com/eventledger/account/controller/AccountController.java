package com.eventledger.account.controller;

import com.eventledger.account.service.AccountService;
import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.ErrorResponse;
import com.eventledger.dto.TransactionRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<?> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transaction request: eventId={}, accountId={}", request.getEventId(), accountId);

        if (!accountId.equals(request.getAccountId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "Bad Request",
                            "accountId in path does not match accountId in body"));
        }

        try {
            AccountResponse response = accountService.applyTransaction(request);
            return ResponseEntity.ok(response);
        } catch (AccountService.AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        }
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId) {
        try {
            AccountResponse response = accountService.getBalance(accountId);
            return ResponseEntity.ok(response);
        } catch (AccountService.AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        }
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId) {
        try {
            AccountResponse response = accountService.getAccount(accountId);
            return ResponseEntity.ok(response);
        } catch (AccountService.AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(java.util.Map.of(
                "status", "UP",
                "database", "UP"
        ));
    }
}
