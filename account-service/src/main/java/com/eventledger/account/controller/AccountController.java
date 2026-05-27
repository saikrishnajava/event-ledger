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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
        } catch (AccountService.CurrencyMismatchException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (AccountService.AccountConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, "Conflict", e.getMessage()));
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.put(error.getField(), error.getDefaultMessage()));
        ErrorResponse error = new ErrorResponse(400, "Validation Failed", "Invalid request fields");
        error.setDetails(details);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
