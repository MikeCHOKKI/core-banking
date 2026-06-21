package com.corebanking.api.controller;

import com.corebanking.ledger.entity.DomainEvent;
import com.corebanking.ledger.service.EventStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final EventStoreService eventStoreService;

    @GetMapping("/events/{aggregateId}")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<List<DomainEvent>> getEvents(@PathVariable UUID aggregateId) {
        return ResponseEntity.ok(eventStoreService.getEventsForAggregate(aggregateId));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<?> getStats() {
        long count = eventStoreService.getEventCount();
        return ResponseEntity.ok(new java.util.HashMap<>(java.util.Map.of("totalEvents", count)));
    }
}
