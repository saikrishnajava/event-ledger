# Event Ledger — Architecture Diagram

## System Architecture

```mermaid
graph TB
    subgraph "External"
        CLIENT[Browser / Client]
    end

    subgraph "Event Gateway :8080"
        GW_RL[RateLimiter<br/>100 req/s]
        GW_CTRL[EventController]
        GW_SVC[EventService]
        GW_REPO[(EventRepository<br/>H2: gateway_db)]
        GW_CLIENT[AccountServiceClient<br/>Retry → CircuitBreaker]
        GW_REPLAY[EventReplayScheduler<br/>@Scheduled 30s]
        GW_FILTER[TracingFilter]
        GW_OTEL[OpenTelemetry SDK]
        GW_METRICS[MetricsConfig]
    end

    subgraph "Account Service :8081"
        AS_CTRL[AccountController]
        AS_SVC[AccountService<br/>@Version optimistic lock]
        AS_ACCT[(AccountRepository<br/>H2: account_db)]
        AS_TXN[(TransactionRepository<br/>H2: account_db)]
        AS_FILTER[TracingFilter]
        AS_OTEL[OpenTelemetry SDK]
    end

    subgraph "Observability"
        JAEGER[Jaeger<br/>:16686 UI]
        PROMETHEUS[Prometheus<br/>:9090]
    end

    CLIENT -->|"POST /events (100 req/s max)"| GW_RL
    GW_RL --> GW_CTRL
    GW_CTRL -->|"GET /events"| GW_SVC
    GW_CTRL --> GW_SVC
    GW_SVC --> GW_REPO
    GW_SVC --> GW_CLIENT
    GW_CLIENT -->|"POST /accounts/{id}/transactions<br/>traceparent header"| AS_CTRL
    GW_REPLAY -->|"retry every 30s"| GW_SVC
    GW_REPLAY --> GW_REPO
    GW_FILTER -.->|"trace ID gen/extract"| GW_CTRL
    GW_OTEL -.->|"spans"| JAEGER
    GW_METRICS -.->|"counters + timers"| GW_CTRL
    GW_METRICS -.->|"export"| PROMETHEUS
    AS_CTRL --> AS_SVC
    AS_SVC --> AS_ACCT
    AS_SVC --> AS_TXN
    AS_FILTER -.->|"trace ID extraction"| AS_CTRL
    AS_OTEL -.->|"spans"| JAEGER
```

## Request Flow (Success Path)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Event Gateway
    participant GWDB as Gateway H2
    participant AS as Account Service
    participant ASDB as Account H2

    C->>GW: POST /events {eventId, type, amount...}
    GW->>GW: Rate Limiter check (100 req/s)
    GW->>GW: Validate payload
    GW->>GWDB: Check eventId exists?
    GWDB-->>GW: Not found
    GW->>GWDB: INSERT event (status=ACCEPTED)
    GW->>AS: POST /accounts/{id}/transactions (traceparent)
    Note over GW,AS: Retry (3x, backoff+jitter) → Circuit Breaker
    AS->>ASDB: Check eventId exists?
    ASDB-->>AS: Not found
    AS->>ASDB: INSERT transaction
    AS->>ASDB: UPDATE account balance (@Version check)
    AS-->>GW: 200 OK {accountId, balance}
    GW->>GWDB: UPDATE event (status=APPLIED)
    GW-->>C: 201 Created {eventId, status: APPLIED}
```

## Request Flow (Async Fallback)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Event Gateway
    participant GWDB as Gateway H2
    participant REPLAY as EventReplayScheduler
    participant AS as Account Service

    C->>GW: POST /events
    GW->>GWDB: INSERT event (status=ACCEPTED)
    GW->>AS: POST /accounts/{id}/transactions
    AS-->>GW: Unreachable (circuit open)
    GW->>GWDB: UPDATE event (status=PENDING)
    GW-->>C: 202 Accepted {status: PENDING}

    Note over REPLAY,AS: 30 seconds later...

    REPLAY->>GWDB: FIND PENDING events
    GWDB-->>REPLAY: [evt-001, evt-002, ...]
    loop For each PENDING event
        REPLAY->>AS: Retry transaction
        AS-->>REPLAY: 200 OK
        REPLAY->>GWDB: UPDATE event (status=APPLIED)
    end
```

## Resilience Layers

```mermaid
graph LR
    REQ[Request] --> RL[Rate Limiter<br/>100 req/s → 429]
    RL --> RT[Retry<br/>3x, backoff+jitter]
    RT --> CB[Circuit Breaker<br/>50% threshold, 20s open]
    CB --> AF[Async Fallback<br/>PENDING queue]
    AF --> AS[Account Service]
```

## Circuit Breaker States

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: Failure rate >= 50%<br/>(sliding window: 10 calls)
    OPEN --> HALF_OPEN: After 20s wait duration
    HALF_OPEN --> CLOSED: 3 probe calls succeed
    HALF_OPEN --> OPEN: Any probe call fails
```
