package com.eventledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AccountResponse {

    private String accountId;
    private BigDecimal balance;
    private Instant updatedAt;
    private List<TransactionSummary> recentTransactions;

    public AccountResponse() {}

    public static class TransactionSummary {
        private String eventId;
        private String type;
        private BigDecimal amount;
        private Instant eventTimestamp;
        private Instant appliedAt;

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Instant getEventTimestamp() { return eventTimestamp; }
        public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
        public Instant getAppliedAt() { return appliedAt; }
        public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<TransactionSummary> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<TransactionSummary> recentTransactions) { this.recentTransactions = recentTransactions; }
}
