package com.eventledger.gateway.service;

import com.eventledger.dto.*;
import com.eventledger.gateway.model.EventEntity;
import com.eventledger.gateway.model.EventStatus;
import com.eventledger.gateway.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;

    public EventService(EventRepository eventRepository, AccountServiceClient accountServiceClient) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
    }

    @Transactional
    public EventResponse processEvent(EventRequest request) {
        log.info("Processing event: eventId={}, accountId={}, type={}, amount={}",
                request.getEventId(), request.getAccountId(), request.getType(), request.getAmount());

        var existing = eventRepository.findByEventId(request.getEventId());
        if (existing.isPresent()) {
            log.info("Duplicate event detected: eventId={}, returning existing", request.getEventId());
            return toEventResponse(existing.get());
        }

        EventEntity event = new EventEntity();
        event.setEventId(request.getEventId());
        event.setAccountId(request.getAccountId());
        event.setType(request.getType());
        event.setAmount(request.getAmount());
        event.setCurrency(request.getCurrency());
        event.setEventTimestamp(request.getEventTimestamp());
        event.setMetadata(request.getMetadata());
        event.setStatus(EventStatus.ACCEPTED);

        try {
            event = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Race condition on duplicate eventId={}, returning existing", request.getEventId());
            return toEventResponse(eventRepository.findByEventId(request.getEventId()).orElseThrow());
        }

        try {
            TransactionRequest txnRequest = new TransactionRequest();
            txnRequest.setEventId(request.getEventId());
            txnRequest.setAccountId(request.getAccountId());
            txnRequest.setType(request.getType());
            txnRequest.setAmount(request.getAmount());
            txnRequest.setCurrency(request.getCurrency());
            txnRequest.setEventTimestamp(request.getEventTimestamp());

            accountServiceClient.applyTransaction(txnRequest);

            event.setStatus(EventStatus.APPLIED);
            event = eventRepository.save(event);
            log.info("Event applied successfully: eventId={}", request.getEventId());
        } catch (AccountServiceClient.AccountServiceUnavailableException e) {
            log.error("Account Service unavailable for eventId={}", request.getEventId());
            throw e;
        } catch (Exception e) {
            log.error("Failed to apply event to Account Service: eventId={}, error={}",
                    request.getEventId(), e.getMessage());
            event.setStatus(EventStatus.REJECTED);
            eventRepository.save(event);
            throw e;
        }

        return toEventResponse(event);
    }

    public EventResponse getEvent(String eventId) {
        EventEntity event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toEventResponse(event);
    }

    public List<EventResponse> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    private EventResponse toEventResponse(EventEntity event) {
        EventResponse response = new EventResponse();
        response.setEventId(event.getEventId());
        response.setAccountId(event.getAccountId());
        response.setType(event.getType().name());
        response.setAmount(event.getAmount());
        response.setCurrency(event.getCurrency());
        response.setEventTimestamp(event.getEventTimestamp());
        response.setStatus(event.getStatus().name());
        response.setReceivedAt(event.getReceivedAt());
        response.setMetadata(event.getMetadata());
        return response;
    }

    public static class EventNotFoundException extends RuntimeException {
        public EventNotFoundException(String eventId) {
            super("Event not found: " + eventId);
        }
    }
}
