package com.eventledger.account.model;

import com.eventledger.dto.EventRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_txn_event_id", columnList = "eventId", unique = true),
    @Index(name = "idx_txn_account_id", columnList = "accountId")
})
public class TransactionEntity {

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
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant appliedAt;

    @PrePersist
    protected void onCreate() {
        appliedAt = Instant.now();
    }

    public TransactionEntity() {}

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public EventRequest.EventType getType() { return type; }
    public void setType(EventRequest.EventType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    public Instant getAppliedAt() { return appliedAt; }
}
