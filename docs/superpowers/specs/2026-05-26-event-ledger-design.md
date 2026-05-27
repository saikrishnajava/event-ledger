# Event Ledger — Design Specification

**Date:** 2026-05-26
**Stack:** Java 21 + Spring Boot 3.5.14 + Maven (multi-module)

---

## 1. Overview

The **Event Ledger** is a system of two microservices that process financial transaction events (CREDIT/DEBIT) from upstream systems that may produce out-of-order and duplicate events.

### Services

| Service | Role | Port | DB |
|---|---|---|---|
| **Event Gateway API** | Public entry point — validates, stores events, enforces idempotency, calls Account Service | 8080 | H2 `gateway_db` |
| **Account Service** | Internal — manages account balances, transaction history | 8081 | H2 `account_db` |

### Architecture Diagram

```
Browser/Client ──→  Event Gateway (:8080) ──→ Account Service (:8081)
                         │        H2              │        H2
                         │   (gateway_db)         │   (account_db)
                         │                        │
                    Stores events             Stores accounts
                    + event history            + transactions
```

---

## 2. API Contracts

### 2.1 Event Gateway API (public-facing)

**POST /events** — Submit a transaction event
- Request body: EventRequest (JSON)
- Success: `201 Created` with EventResponse
- Duplicate eventId: `200 OK` with existing EventResponse (idempotent)
- Validation failure: `400 Bad Request` with error details
- Circuit open: `503 Service Unavailable`
- Account Service error: `502 Bad Gateway`

**GET /events/{id}** — Retrieve a single event
- Success: `200 OK` with EventResponse
- Not found: `404 Not Found`
- Works independently of Account Service availability

**GET /events?account={accountId}** — List events for an account
- Success: `200 OK` with array of EventResponse, sorted chronologically by eventTimestamp
- Works independently of Account Service availability

**GET /health** — Health check
- `200 OK` with `{ "status": "UP", "database": "UP", "accountService": "UP|DOWN" }`

### 2.2 Account Service (internal)

**POST /accounts/{accountId}/transactions** — Apply a transaction
- Request body: TransactionRequest (eventId, type, amount, currency, timestamp)
- Success: `200 OK` with updated AccountResponse
- Duplicate eventId: `200 OK` with existing AccountResponse (idempotent)

**GET /accounts/{accountId}/balance** — Get current balance
- Success: `200 OK` with `{ "accountId": "...", "balance": 150.00 }`

**GET /accounts/{accountId}** — Get account details + recent transactions (last 20)
- Success: `200 OK` with AccountResponse

**GET /health** — Health check
- `200 OK` with `{ "status": "UP", "database": "UP" }`

---

## 3. Data Models

### 3.1 EventRequest (Gateway input)
```
eventId: String (required, unique)
accountId: String (required)
type: enum CREDIT | DEBIT (required)
amount: BigDecimal > 0 (required)
currency: String (required, e.g. "USD")
eventTimestamp: Instant (required, ISO-8601)
metadata: Map<String, String> (optional)
```

### 3.2 EventEntity (Gateway persistence)
Same as EventRequest plus:
- `id`: auto-generated Long (PK)
- `receivedAt`: Instant (when Gateway received it)
- `status`: enum ACCEPTED | APPLIED | REJECTED

### 3.3 AccountEntity (Account Service persistence)
- `accountId`: String (PK)
- `balance`: BigDecimal (net = sum CREDITs - sum DEBITs)
- `createdAt`: Instant
- `updatedAt`: Instant

### 3.4 TransactionEntity (Account Service persistence)
- `id`: auto-generated Long (PK)
- `eventId`: String (unique index for idempotency)
- `accountId`: String (FK to AccountEntity)
- `type`: enum CREDIT | DEBIT
- `amount`: BigDecimal
- `eventTimestamp`: Instant
- `appliedAt`: Instant

---

## 4. Idempotency Strategy

**Gateway:** Unique constraint on `eventId` column in EventEntity. On duplicate POST, catch `DataIntegrityViolationException`, query the existing event, return `200 OK` (not 201).

**Account Service:** Unique constraint on `eventId` column in TransactionEntity. On duplicate POST, catch `DataIntegrityViolationException`, return existing account state.

Both services use `@Transactional` to ensure atomicity of the check-and-insert or check-and-update flow.

---

## 5. Out-of-Order Tolerance

- `GET /events?account={accountId}` returns events sorted by `eventTimestamp ASC` via JPA `findByAccountIdOrderByEventTimestampAsc()`.
- Balance computation is commutative — sum of CREDITs minus sum of DEBITs produces the same result regardless of apply order.
- Individual transactions are always applied; the timestamp determines display order, not apply order.

---

## 6. Distributed Tracing

W3C Trace Context propagation using the `traceparent` header:

1. **Gateway filter** (`TracingFilter`): On each incoming request, checks for `traceparent` header. If absent, generates a new trace ID and span ID per W3C spec format (`00-{traceId}-{spanId}-01`). Stores in SLF4J MDC.
2. **Gateway RestClient interceptor**: Injects `traceparent` into outgoing Account Service requests.
3. **Account Service filter**: Extracts `traceparent` from incoming request, stores in MDC.
4. **Both services**: Log structured JSON with `{ traceId, spanId, service, timestamp, level, message }` using Logstash Logback Encoder.

---

## 7. Resiliency — Circuit Breaker

**Pattern:** Resilience4j Circuit Breaker on `AccountServiceClient` in the Gateway.

**Configuration:**
```yaml
resilience4j.circuitbreaker:
  instances:
    accountService:
      sliding-window-type: COUNT_BASED
      sliding-window-size: 10
      failure-rate-threshold: 50
      wait-duration-in-open-state: 20s
      permitted-number-of-calls-in-half-open-state: 3
      automatic-transition-from-open-to-half-open-enabled: true
```

**Behavior:**
- Closed: Normal operation. Failures tracked in sliding window.
- Open: Immediate `CallNotPermittedException` → Gateway returns `503 Service Unavailable`.
- Half-Open: Allow 3 probe calls. If they succeed → Closed. If any fail → Open again.

**Fallback:** `@CircuitBreaker(name = "accountService", fallbackMethod = "accountServiceFallback")`
Fallback method returns a `ResponseEntity<ErrorResponse>` with 503 status.

---

## 8. Graceful Degradation

| Scenario | Behavior |
|---|---|
| Account Service unreachable | `POST /events` returns `503`; reads still work |
| Account Service slow | Circuit breaker opens after threshold, fail-fast with `503` |
| Account Service partially responding | Half-open probe calls determine recovery |
| Gateway DB issue | Health check reports `DOWN`; endpoints return `500` |

Read-only endpoints (`GET /events/{id}`, `GET /events?account=...`) never depend on Account Service and always function when Gateway is healthy.

---

## 9. Observability

### 9.1 Structured Logging
Logstash Logback Encoder producing JSON lines:
```json
{"@timestamp":"2026-05-26T14:02:11.123Z","level":"INFO","service":"event-gateway","traceId":"abc123","message":"Event accepted","eventId":"evt-001"}
```

### 9.2 Health Checks
Spring Boot Actuator health endpoint + custom `HealthIndicator` for:
- Gateway: H2 connectivity + Account Service reachability
- Account Service: H2 connectivity

### 9.3 Custom Metrics (Micrometer)
- `events.submitted.total` (Counter) — total events submitted
- `events.duplicate.total` (Counter) — duplicate event submissions
- `events.processing.time` (Timer) — end-to-end processing time
- `circuitbreaker.state` (Gauge) — current circuit breaker state

Exposed via Actuator `/actuator/metrics` and in structured logs.

---

## 10. Testing Strategy

### Unit Tests (JUnit 5 + Mockito)
- Idempotency: Duplicate eventId returns existing event (both services)
- Out-of-order: Events returned in chronological order
- Validation: Missing fields, negative/zero amounts, invalid types
- Balance: Sum of CREDITs - Sum of DEBITs
- Trace propagation: TraceIdFilter generates/parses W3C headers

### Integration Tests (Spring Boot Test + MockMvc / TestRestTemplate)
- Full Gateway → Account Service flow
- Circuit breaker: Simulate Account Service failure, verify circuit opens
- Graceful degradation: Verify read endpoints work during Account Service outage
- Trace propagation: Verify traceId flows across services

### Running Tests
```bash
mvn test                           # Unit + Integration tests
mvn verify                         # Including coverage (JaCoCo)
```

### Coverage Targets
- Unit test coverage: >80% line coverage
- Functional: All API endpoints covered by integration tests

---

## 11. Docker Compose

```yaml
services:
  event-gateway:
    build: ./event-gateway
    ports: ["8080:8080"]
    environment:
      - ACCOUNT_SERVICE_URL=http://account-service:8081
      - SPRING_DATASOURCE_URL=jdbc:h2:mem:gateway_db
    depends_on: [account-service]

  account-service:
    build: ./account-service
    ports: ["8081:8081"]
    environment:
      - SPRING_DATASOURCE_URL=jdbc:h2:mem:account_db
```

---

## 12. Maven Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API framework |
| `spring-boot-starter-data-jpa` | JPA + Hibernate |
| `spring-boot-starter-actuator` | Health checks, metrics |
| `spring-boot-starter-validation` | Bean validation (Jakarta) |
| `com.h2database:h2` | Embedded database |
| `io.github.resilience4j:resilience4j-spring-boot3` | Circuit breaker |
| `net.logstash.logback:logstash-logback-encoder` | JSON structured logging |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | API documentation |
| `spring-boot-starter-test` | Testing (JUnit 5, Mockito) |
| `org.jacoco:jacoco-maven-plugin` | Code coverage |
