package com.eventledger.account.controller;

import com.eventledger.account.service.AccountService;
import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.EventRequest;
import com.eventledger.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AccountService accountService = mock(AccountService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(accountService))
                .build();
    }

    @Test
    void shouldReturn404ForUnknownAccount() throws Exception {
        when(accountService.getBalance("unknown"))
                .thenThrow(new AccountService.AccountNotFoundException("unknown"));

        mockMvc.perform(get("/accounts/unknown/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnBalance() throws Exception {
        AccountResponse resp = new AccountResponse();
        resp.setAccountId("acct-123");
        resp.setBalance(BigDecimal.valueOf(500));
        when(accountService.getBalance("acct-123")).thenReturn(resp);

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500))
                .andExpect(jsonPath("$.accountId").value("acct-123"));
    }

    @Test
    void shouldReturnBadRequestWhenAccountIdMismatch() throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setEventId("evt-001");
        req.setAccountId("acct-999");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

}
