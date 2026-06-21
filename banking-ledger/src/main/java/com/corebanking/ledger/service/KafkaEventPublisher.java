package com.corebanking.ledger.service;

import com.corebanking.common.event.EventPublisher;
import com.corebanking.ledger.entity.DomainEvent;
import com.corebanking.ledger.repository.EventStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation de {@link EventPublisher} qui persiste les événements
 * dans l'event store PostgreSQL puis les publie sur Kafka via Spring Cloud Stream.
 * <p>
 * Le message Kafka inclut un eventId (UUID) au niveau racine, un version calculée
 * à partir du nombre d'événements existants pour l'agrégat, et l'ensemble des
 * données métier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private static final String KAFKA_TOPIC = "banking.events";

    private final StreamBridge streamBridge;
    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publish(String aggregateType, String aggregateId, String eventType,
                        Map<String, Object> data, Map<String, Object> metadata) {
        UUID eventId = UUID.randomUUID();
        UUID aggId = UUID.fromString(aggregateId);
        long version = resolveNextVersion(aggId);

        // 1. Persister dans l'event store
        DomainEvent domainEvent = DomainEvent.builder()
                .id(eventId)
                .aggregateId(aggId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .version(version)
                .data(toJson(data))
                .metadata(toJson(metadata))
                .build();

        eventStoreRepository.save(domainEvent);
        log.debug("Event persisted: {} (v{}) for aggregate {}", eventType, version, aggregateId);

        // 2. Publier sur Kafka
        Map<String, Object> kafkaMessage = buildMessage(eventId, aggregateType, aggregateId,
                eventType, version, data, metadata, null);
        streamBridge.send(KAFKA_TOPIC, kafkaMessage);
        log.info("Event published to Kafka: {} (id={})", eventType, eventId);
    }

    @Override
    @Transactional
    public void publishWithIdempotency(String aggregateType, String aggregateId, String eventType,
                                       Map<String, Object> data, Map<String, Object> metadata,
                                       String idempotencyKey) {
        // Vérification de déduplication
        if (eventStoreRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate event detected, skipping: idempotencyKey={}", idempotencyKey);
            return;
        }

        UUID eventId = UUID.randomUUID();
        UUID aggId = UUID.fromString(aggregateId);
        long version = resolveNextVersion(aggId);

        // 1. Persister dans l'event store
        DomainEvent domainEvent = DomainEvent.builder()
                .id(eventId)
                .aggregateId(aggId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .version(version)
                .data(toJson(data))
                .metadata(toJson(metadata))
                .idempotencyKey(idempotencyKey)
                .build();

        eventStoreRepository.save(domainEvent);
        log.debug("Event persisted (idempotent): {} (v{}) key={}", eventType, version, idempotencyKey);

        // 2. Publier sur Kafka
        Map<String, Object> kafkaMessage = buildMessage(eventId, aggregateType, aggregateId,
                eventType, version, data, metadata, idempotencyKey);
        streamBridge.send(KAFKA_TOPIC, kafkaMessage);
        log.info("Event published to Kafka (idempotent): {} (id={})", eventType, eventId);
    }

    /**
     * Construit le message Kafka structuré avec eventId au niveau racine.
     */
    private Map<String, Object> buildMessage(UUID eventId, String aggregateType, String aggregateId,
                                              String eventType, long version,
                                              Map<String, Object> data, Map<String, Object> metadata,
                                              String idempotencyKey) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("eventId", eventId.toString());
        message.put("aggregateId", aggregateId);
        message.put("aggregateType", aggregateType);
        message.put("eventType", eventType);
        message.put("version", version);
        message.put("data", data);
        message.put("metadata", metadata);
        if (idempotencyKey != null) {
            message.put("idempotencyKey", idempotencyKey);
        }
        message.put("createdAt", OffsetDateTime.now().toString());
        return message;
    }

    /**
     * Calcule le prochain numéro de version pour un agrégat
     * en fonction du nombre d'événements déjà stockés.
     */
    private long resolveNextVersion(UUID aggregateId) {
        Long count = eventStoreRepository.countByAggregateId(aggregateId);
        return (count != null ? count : 0L) + 1L;
    }

    /**
     * Sérialise une valeur en JSON pour le stockage dans l'event store.
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize to JSON: {}", value, e);
            throw new RuntimeException("JSON serialization failed for event data", e);
        }
    }
}
