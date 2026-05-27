package com.eventledger.gateway.service;

import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.TransactionRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "accountService";

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Retry(name = CIRCUIT_BREAKER_NAME)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "applyTransactionFallback")
    public AccountResponse applyTransaction(TransactionRequest request) {
        log.info("Calling Account Service: accountId={}, eventId={}", request.getAccountId(), request.getEventId());

        return restClient.post()
                .uri("/accounts/{accountId}/transactions", request.getAccountId())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Account Service error: status={}, eventId={}", res.getStatusCode(), request.getEventId());
                    throw new AccountServiceException("Account Service returned " + res.getStatusCode());
                })
                .body(AccountResponse.class);
    }

    @SuppressWarnings("unused")
    private AccountResponse applyTransactionFallback(TransactionRequest request, Throwable t) {
        log.error("Circuit breaker fallback: eventId={}, reason={}", request.getEventId(), t.getMessage());
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable. Please try again later.", t);
    }

    public static class AccountServiceException extends RuntimeException {
        public AccountServiceException(String message) {
            super(message);
        }
    }

    public static class AccountServiceUnavailableException extends RuntimeException {
        public AccountServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
