package com.eventledger.account.integration;

import com.eventledger.dto.EventRequest;
import com.eventledger.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AccountServiceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldApplyCreditAndUpdateBalance() throws Exception {
        String accountId = "iacct-" + System.currentTimeMillis();
        TransactionRequest req = buildRequest("evt-" + System.currentTimeMillis(), accountId, "CREDIT", BigDecimal.valueOf(200));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200));
    }

    @Test
    void shouldHandleDebitTransaction() throws Exception {
        String accountId = "dacct-" + System.currentTimeMillis();

        TransactionRequest creditReq = buildRequest("devt-credit-" + System.currentTimeMillis(), accountId, "CREDIT", BigDecimal.valueOf(500));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creditReq)));

        TransactionRequest debitReq = buildRequest("devt-debit-" + System.currentTimeMillis(), accountId, "DEBIT", BigDecimal.valueOf(200));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300));
    }

    @Test
    void shouldBeIdempotentForDuplicateTransactions() throws Exception {
        String accountId = "idem-" + System.currentTimeMillis();
        String eventId = "idem-ev-" + System.currentTimeMillis();
        TransactionRequest req = buildRequest(eventId, accountId, "CREDIT", BigDecimal.valueOf(100));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
    }

    private TransactionRequest buildRequest(String eventId, String accountId, String type, BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        req.setEventId(eventId);
        req.setAccountId(accountId);
        req.setType(EventRequest.EventType.valueOf(type));
        req.setAmount(amount);
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());
        return req;
    }
}
