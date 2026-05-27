package com.eventledger.gateway.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.eventledger.dto.AccountResponse;
import com.eventledger.dto.EventRequest;
import com.eventledger.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "account-service", port = "8888")
class GatewayPactConsumerTest {

    @Pact(consumer = "event-gateway")
    public V4Pact applyTransactionPact(PactDslWithProvider builder) {
        return builder
            .given("account acct-123 exists")
            .uponReceiving("a CREDIT transaction request")
                .path("/accounts/acct-123/transactions")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body("{\"eventId\":\"pact-001\",\"accountId\":\"acct-123\"," +
                      "\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\"," +
                      "\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}")
            .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body("{\"accountId\":\"acct-123\",\"balance\":100.00," +
                      "\"updatedAt\":null,\"recentTransactions\":[]}")
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "applyTransactionPact")
    void shouldApplyTransactionPerContract(MockServer mockServer) throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setEventId("pact-001");
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String jsonBody = mapper.writeValueAsString(req);

        AccountResponse resp = RestClient.create()
            .post().uri(mockServer.getUrl() + "/accounts/acct-123/transactions")
            .header("Content-Type", "application/json")
            .body(jsonBody).retrieve().body(AccountResponse.class);

        assertThat(resp).isNotNull();
        assertThat(resp.getAccountId()).isEqualTo("acct-123");
        assertThat(resp.getBalance()).isEqualByComparingTo("100.00");
    }
}
