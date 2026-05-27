# Event Ledger — Bonus Features Design

**Date:** 2026-05-26

---

## 1. Async Fallback — Queue & Replay

### Problem
When Account Service is unreachable and circuit breaker is open, events are rejected with 503. A more resilient system should accept events and retry them when the downstream service recovers.

### Design
- Add `PENDING` status to `EventStatus` enum
- `EventService.processEvent()`: catch `AccountServiceUnavailableException`, save event with `PENDING` status, return `202 Accepted`
- New `EventReplayScheduler.java`: `@Scheduled(fixedDelay=30000)` scans for PENDING events, retries Account Service call, updates to `APPLIED`
- `EventController.submitEvent()`: return 202 for events saved as PENDING
- Idempotency still works — if a PENDING event is resubmitted, the duplicate check catches it

### Files
- Modify: `EventStatus.java`, `EventService.java`, `EventController.java`
- Create: `EventReplayScheduler.java`
- Create: `EventReplaySchedulerTest.java`

---

## 2. OpenTelemetry + Jaeger

### Design
- Add `opentelemetry-bom`, `opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp` deps
- Keep existing W3C TracingFilter (it works) — add OTel `Span` creation alongside it
- New `OpenTelemetryConfig.java`: creates `Tracer`, `SdkTracerProvider` with OTLP exporter
- `docker-compose.yml`: add Jaeger all-in-one service, UI at `:16686`
- Both services export spans to Jaeger via OTLP

### Files
- Modify: `pom.xml` (parent, for BOM)
- Modify: `event-gateway/pom.xml`, `account-service/pom.xml`
- Create: `event-gateway/config/OpenTelemetryConfig.java`
- Create: `account-service/config/OpenTelemetryConfig.java`
- Modify: `docker-compose.yml`

---

## 3. Contract Tests with Pact

### Design
- Account Service (Provider): generates pact contract file
- Gateway (Consumer): defines expectations, verifies against pact
- File-based pact exchange (no broker)
- Test: `AccountServicePactProviderTest` + `GatewayPactConsumerTest`

### Files
- Modify: `account-service/pom.xml`, `event-gateway/pom.xml`
- Create: `account-service/test/.../AccountServicePactProviderTest.java`
- Create: `event-gateway/test/.../GatewayPactConsumerTest.java`
