package com.eventledger.gateway.repository;

import com.eventledger.gateway.model.EventEntity;
import com.eventledger.gateway.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {
    Optional<EventEntity> findByEventId(String eventId);
    List<EventEntity> findByAccountIdOrderByEventTimestampAsc(String accountId);
    List<EventEntity> findByStatusOrderByReceivedAtAsc(EventStatus status);
}
