package com.eventledger.gateway.service;

import com.eventledger.dto.EventRequest;
import com.eventledger.gateway.model.EventEntity;
import com.eventledger.gateway.model.EventStatus;
import com.eventledger.gateway.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private AccountServiceClient accountServiceClient;
    @InjectMocks private EventService eventService;

    @Test
    void shouldReturnEventsInChronologicalOrder() {
        EventEntity oldEvent = createEvent("evt-001", Instant.parse("2026-01-01T10:00:00Z"));
        EventEntity newEvent = createEvent("evt-002", Instant.parse("2026-02-01T10:00:00Z"));

        when(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-123"))
                .thenReturn(List.of(oldEvent, newEvent));

        var events = eventService.getEventsByAccount("acct-123");
        assertEquals(2, events.size());
        assertTrue(events.get(0).getEventTimestamp().isBefore(events.get(1).getEventTimestamp()));
    }

    @Test
    void shouldReturnExistingEventOnDuplicate() {
        EventRequest request = createRequest("evt-001");
        EventEntity existing = createEvent("evt-001", Instant.now());

        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existing));

        var result = eventService.processEvent(request);
        assertEquals("evt-001", result.getEventId());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnSortedEventsByTimestamp() {
        EventEntity e1 = createEvent("evt-003", Instant.parse("2026-03-01T10:00:00Z"));
        EventEntity e2 = createEvent("evt-001", Instant.parse("2026-01-01T10:00:00Z"));
        EventEntity e3 = createEvent("evt-002", Instant.parse("2026-02-01T10:00:00Z"));

        when(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-123"))
                .thenReturn(List.of(e2, e3, e1));

        var events = eventService.getEventsByAccount("acct-123");
        assertEquals("evt-001", events.get(0).getEventId());
        assertEquals("evt-002", events.get(1).getEventId());
        assertEquals("evt-003", events.get(2).getEventId());
    }

    @Test
    void shouldThrowNotFoundExceptionForUnknownEvent() {
        when(eventRepository.findByEventId("unknown")).thenReturn(Optional.empty());
        assertThrows(EventService.EventNotFoundException.class,
                () -> eventService.getEvent("unknown"));
    }

    private EventRequest createRequest(String eventId) {
        EventRequest req = new EventRequest();
        req.setEventId(eventId);
        req.setAccountId("acct-123");
        req.setType(EventRequest.EventType.CREDIT);
        req.setAmount(BigDecimal.valueOf(100));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.now());
        return req;
    }

    private EventEntity createEvent(String eventId, Instant timestamp) {
        EventEntity e = new EventEntity();
        e.setEventId(eventId);
        e.setAccountId("acct-123");
        e.setType(EventRequest.EventType.CREDIT);
        e.setAmount(BigDecimal.valueOf(100));
        e.setCurrency("USD");
        e.setEventTimestamp(timestamp);
        e.setStatus(EventStatus.APPLIED);
        return e;
    }
}
