package com.eventledger.gateway.integration;

import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.EventRequest;
import com.eventledger.gateway.service.AccountServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GatewayIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AccountServiceClient accountServiceClient;

    @Test
    void shouldReturnHealthUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldHandleDuplicateEvent() throws Exception {
        String eventId = "itg-dup-" + System.currentTimeMillis();
        AccountResponse mockResp = new AccountResponse();
        mockResp.setAccountId("acct-123");
        mockResp.setBalance(BigDecimal.valueOf(100));
        when(accountServiceClient.applyTransaction(any())).thenReturn(mockResp);

        EventRequest req = buildRequest(eventId);
        String json = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(json));
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void shouldListEventsForAccount() throws Exception {
        String eventId = "itg-list-ev1-" + System.currentTimeMillis();
        AccountResponse mockResp = new AccountResponse();
        mockResp.setAccountId("acct-123");
        mockResp.setBalance(BigDecimal.valueOf(100));
        when(accountServiceClient.applyTransaction(any())).thenReturn(mockResp);

        EventRequest req = buildRequest(eventId);
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(get("/events?account=acct-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldRejectNegativeAmount() throws Exception {
        EventRequest req = new EventRequest();
        req.setEventId("itg-neg-" + System.currentTimeMillis());
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(-50));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn503WhenAccountServiceThrows() throws Exception {
        when(accountServiceClient.applyTransaction(any()))
                .thenThrow(new AccountServiceClient.AccountServiceUnavailableException("down", new RuntimeException()));

        EventRequest req = buildRequest("itg-503-" + System.currentTimeMillis());
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    private EventRequest buildRequest(String eventId) {
        EventRequest req = new EventRequest();
        req.setEventId(eventId);
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());
        return req;
    }
}
