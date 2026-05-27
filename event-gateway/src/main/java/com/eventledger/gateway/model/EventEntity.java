package com.eventledger.gateway.model;

import com.eventledger.dto.EventRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_event_event_id", columnList = "eventId", unique = true),
    @Index(name = "idx_event_account_id", columnList = "accountId"),
    @Index(name = "idx_event_timestamp", columnList = "eventTimestamp")
})
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventRequest.EventType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_metadata", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata;

    @PrePersist
    protected void onCreate() {
        receivedAt = Instant.now();
        if (status == null) {
            status = EventStatus.ACCEPTED;
        }
    }

    public EventEntity() {}

    public Long getId() { return id; }
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
    public Instant getReceivedAt() { return receivedAt; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
