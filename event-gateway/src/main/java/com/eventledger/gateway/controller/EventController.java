package com.eventledger.gateway.controller;

import com.eventledger.dto.ErrorResponse;
import com.eventledger.dto.EventRequest;
import com.eventledger.dto.EventResponse;
import com.eventledger.gateway.config.GatewayHealthIndicator;
import com.eventledger.gateway.service.AccountServiceClient;
import com.eventledger.gateway.service.EventService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;
    private final GatewayHealthIndicator healthIndicator;
    private final Counter eventsSubmittedCounter;
    private final Counter eventsDuplicateCounter;
    private final Timer eventProcessingTimer;

    public EventController(EventService eventService, MeterRegistry meterRegistry,
                           GatewayHealthIndicator healthIndicator) {
        this.eventService = eventService;
        this.healthIndicator = healthIndicator;
        this.eventsSubmittedCounter = Counter.builder("events.submitted.total")
                .description("Total number of events submitted")
                .register(meterRegistry);
        this.eventsDuplicateCounter = Counter.builder("events.duplicate.total")
                .description("Total number of duplicate event submissions")
                .register(meterRegistry);
        this.eventProcessingTimer = Timer.builder("events.processing.time")
                .description("Event processing time")
                .register(meterRegistry);
    }

    @PostMapping("/events")
    @RateLimiter(name = "eventSubmission")
    public ResponseEntity<?> submitEvent(@Valid @RequestBody EventRequest request) {
        long start = System.nanoTime();
        log.info("Received event submission: eventId={}", request.getEventId());

        try {
            EventResponse response = eventService.processEvent(request);

            if ("APPLIED".equals(response.getStatus())) {
                eventsSubmittedCounter.increment();
            }
            eventProcessingTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);

            if ("REJECTED".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }
            eventsSubmittedCounter.increment();
            if ("PENDING".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            if ("ACCEPTED".equals(response.getStatus()) || "APPLIED".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
            return ResponseEntity.ok(response);

        } catch (AccountServiceClient.AccountServiceUnavailableException e) {
            log.error("Account Service unavailable: eventId={}", request.getEventId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(503, "Service Unavailable", e.getMessage()));
        } catch (AccountServiceClient.AccountServiceException e) {
            log.error("Account Service error: eventId={}", request.getEventId());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse(502, "Bad Gateway", e.getMessage()));
        }
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEvent(@PathVariable String id) {
        try {
            EventResponse response = eventService.getEvent(id);
            return ResponseEntity.ok(response);
        } catch (EventService.EventNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> listEvents(@RequestParam("account") String accountId) {
        List<EventResponse> events = eventService.getEventsByAccount(accountId);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Health accountServiceHealth = healthIndicator.health();
        Map<String, Object> health = new HashMap<>();
        health.put("status", accountServiceHealth.getStatus().getCode());
        health.put("database", "UP");
        health.put("accountService", accountServiceHealth.getStatus().getCode());
        if (accountServiceHealth.getDetails() != null) {
            health.put("accountServiceDetails", accountServiceHealth.getDetails());
        }
        return ResponseEntity.ok(health);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.put(error.getField(), error.getDefaultMessage()));
        ErrorResponse error = new ErrorResponse(400, "Validation Failed", "Invalid request fields");
        error.setDetails(details);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex) {
        log.warn("Rate limit exceeded");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(429, "Too Many Requests", "Rate limit exceeded. Try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
