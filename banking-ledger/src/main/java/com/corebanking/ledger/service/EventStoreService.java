package com.corebanking.ledger.service;

import com.corebanking.ledger.entity.DomainEvent;
import com.corebanking.ledger.repository.EventStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventStoreService {

    private final EventStoreRepository eventStoreRepository;

    public List<DomainEvent> getEventsForAggregate(UUID aggregateId) {
        return eventStoreRepository.findByAggregateIdOrderByVersionAsc(aggregateId);
    }

    public long getEventCount() {
        return eventStoreRepository.count();
    }
}
