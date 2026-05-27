package com.eventledger.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventReplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReplayScheduler.class);
    private final EventService eventService;

    public EventReplayScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    @Scheduled(fixedDelay = 30000)
    public void replayPendingEvents() {
        var pending = eventService.findPendingEvents();
        if (pending.isEmpty()) return;
        log.info("Found {} pending event(s) to replay", pending.size());
        for (var event : pending) {
            try {
                eventService.replayEvent(event.getId());
            } catch (Exception e) {
                log.warn("Failed to replay event {}: {}", event.getEventId(), e.getMessage());
            }
        }
    }
}
