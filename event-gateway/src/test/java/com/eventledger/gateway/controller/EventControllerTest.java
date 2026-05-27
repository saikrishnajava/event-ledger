package com.eventledger.gateway.controller;

import com.eventledger.dto.EventRequest;
import com.eventledger.dto.EventResponse;
import com.eventledger.gateway.config.GatewayHealthIndicator;
import com.eventledger.gateway.service.AccountServiceClient;
import com.eventledger.gateway.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EventService eventService = mock(EventService.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final GatewayHealthIndicator healthIndicator = mock(GatewayHealthIndicator.class);

    @BeforeEach
    void setUp() {
        when(healthIndicator.health()).thenReturn(Health.up().build());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(eventService, meterRegistry, healthIndicator))
                .build();
    }

    @Test
    void shouldReturn404ForUnknownEvent() throws Exception {
        when(eventService.getEvent("unknown"))
                .thenThrow(new EventService.EventNotFoundException("unknown"));

        mockMvc.perform(get("/events/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldRejectInvalidEvent() throws Exception {
        EventRequest req = new EventRequest();
        req.setEventId("evt-001");

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEmptyListForAccountWithNoEvents() throws Exception {
        when(eventService.getEventsByAccount("acct-123"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/events?account=acct-123"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldReturnHealthUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldHandleAccountServiceUnavailable() throws Exception {
        when(eventService.processEvent(any()))
                .thenThrow(new AccountServiceClient.AccountServiceUnavailableException("unavailable", new RuntimeException("test")));

        EventRequest req = new EventRequest();
        req.setEventId("evt-503");
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void shouldAcceptValidEvent() throws Exception {
        EventResponse resp = new EventResponse();
        resp.setEventId("evt-001");
        resp.setStatus("APPLIED");
        when(eventService.processEvent(any())).thenReturn(resp);

        EventRequest req = new EventRequest();
        req.setEventId("evt-001");
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
