package com.eventledger.account.repository;

import com.eventledger.account.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    Optional<TransactionEntity> findByEventId(String eventId);
    List<TransactionEntity> findTop20ByAccountIdOrderByAppliedAtDesc(String accountId);
}
