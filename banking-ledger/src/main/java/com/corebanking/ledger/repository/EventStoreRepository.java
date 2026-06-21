package com.corebanking.ledger.repository;

import com.corebanking.ledger.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventStoreRepository extends JpaRepository<DomainEvent, UUID> {

    List<DomainEvent> findByAggregateIdOrderByVersionAsc(UUID aggregateId);

    Optional<DomainEvent> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Long countByAggregateId(UUID aggregateId);
}
