# Event Ledger

A two-microservice system for processing financial transaction events with idempotency, out-of-order tolerance, distributed tracing, and resiliency patterns.

## Architecture

- **Event Gateway API** (port 8080): Public-facing service that receives transaction events, validates input, enforces idempotency, stores events in H2, and forwards to the Account Service.
- **Account Service** (port 8081): Internal service that manages account balances and transaction history in its own H2 database.

See [Architecture Diagram](docs/architecture-diagram.md) for detailed diagrams.

## Prerequisites

- Java 21 (Oracle JDK)
- Maven 3.9+
- Docker & Docker Compose (optional)

## Setup

```bash
git clone https://github.com/saikrishnajava/event-ledger.git
cd event-ledger
```

## Build

```bash
export JAVA_HOME="path/to/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

## Run with Docker Compose

```bash
docker-compose up --build
```

Services will be available at:
- Event Gateway: http://localhost:8080
- Account Service: http://localhost:8081

## Run Locally (without Docker)

Terminal 1 — Account Service:
```bash
cd account-service
mvn spring-boot:run
```

Terminal 2 — Event Gateway:
```bash
cd event-gateway
mvn spring-boot:run
```

## API Endpoints

### Event Gateway (port 8080)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/events` | Submit a transaction event |
| GET | `/events/{id}` | Get event by ID |
| GET | `/events?account={id}` | List events for account |
| GET | `/health` | Health check |

### Account Service (port 8081)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/accounts/{id}/transactions` | Apply transaction |
| GET | `/accounts/{id}/balance` | Get balance |
| GET | `/accounts/{id}` | Get account details |
| GET | `/health` | Health check |

## Example Usage

```bash
# Submit a credit event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }'

# Get event by ID
curl http://localhost:8080/events/evt-001

# List events for an account
curl "http://localhost:8080/events?account=acct-123"

# Check account balance
curl http://localhost:8081/accounts/acct-123/balance

# Submit a debit event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-002",
    "accountId": "acct-123",
    "type": "DEBIT",
    "amount": 50.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T15:00:00Z"
  }'

# Verify balance (should be 100.00)
curl http://localhost:8081/accounts/acct-123/balance
```

## Testing

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn verify

# Coverage reports at:
# event-gateway/target/site/jacoco/index.html
# account-service/target/site/jacoco/index.html
```

## Resiliency — Circuit Breaker

The Gateway uses Resilience4j Circuit Breaker on calls to the Account Service:
- **Sliding Window:** COUNT_BASED, 10 calls
- **Failure Threshold:** 50%
- **Open State Duration:** 20 seconds
- **Half-Open Probes:** 3 calls

When the circuit is open, `POST /events` returns `503 Service Unavailable`.
Read endpoints (`GET /events/...`) continue to work independently.

## Observability

- **Structured Logging:** JSON-formatted logs via Logstash Logback Encoder with traceId correlation
- **Distributed Tracing:** W3C `traceparent` header propagated across services
- **Metrics:** Micrometer counters and timers exposed via `/actuator/metrics`
  - `events.submitted.total`, `events.duplicate.total`, `events.processing.time`

## Tech Stack

- Java 21 (Oracle JDK)
- Spring Boot 3.5.14
- Maven (multi-module)
- H2 Embedded Database
- Resilience4j Circuit Breaker
- Micrometer + Spring Boot Actuator
- Logstash Logback Encoder
