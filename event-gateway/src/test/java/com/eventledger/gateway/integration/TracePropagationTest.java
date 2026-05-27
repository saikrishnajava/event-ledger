package com.eventledger.gateway.integration;

import com.eventledger.gateway.config.GatewayHealthIndicator;
import com.eventledger.gateway.service.AccountServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TracePropagationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccountServiceClient accountServiceClient;
    @MockitoBean private GatewayHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        when(healthIndicator.health()).thenReturn(Health.up().build());
    }

    @Test
    void shouldGenerateTraceparentWhenNotProvided() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("traceparent",
                        matchesPattern("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")));
    }

    @Test
    void shouldExtractTraceIdFromIncomingTraceparent() throws Exception {
        String incomingTraceparent = "00-abcdef1234567890abcdef1234567890-1234567890abcdef-01";

        mockMvc.perform(get("/health")
                        .header("traceparent", incomingTraceparent))
                .andExpect(status().isOk())
                .andExpect(header().string("traceparent",
                        matchesPattern("^00-abcdef1234567890abcdef1234567890-[0-9a-f]{16}-01$")));
    }

    @Test
    void shouldTraceEventSubmissionEndToEnd() throws Exception {
        String incomingTraceparent = "00-aaaabbbbccccddddeeeeffff00001111-2222333344445555-01";

        mockMvc.perform(post("/events")
                        .header("traceparent", incomingTraceparent)
                        .contentType("application/json")
                        .content("{\"eventId\":\"trace-test-1\",\"accountId\":\"acct-123\"," +
                                "\"type\":\"CREDIT\",\"amount\":100,\"currency\":\"USD\"," +
                                "\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}"))
                .andExpect(header().string("traceparent",
                        matchesPattern("^00-aaaabbbbccccddddeeeeffff00001111-[0-9a-f]{16}-01$")));
    }
}
