package com.eventledger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String accountId;

    @NotNull
    private EventRequest.EventType type;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotBlank
    private String currency;

    @NotNull
    @JsonProperty("eventTimestamp")
    private Instant eventTimestamp;

    public TransactionRequest() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public EventRequest.EventType getType() { return type; }
    public void setType(EventRequest.EventType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
}
