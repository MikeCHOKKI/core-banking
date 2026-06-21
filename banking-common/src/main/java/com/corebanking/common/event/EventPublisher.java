package com.corebanking.common.event;

import java.util.Map;

/**
 * Interface de publication d'événements métier.
 * L'implémentation utilise Spring Cloud Stream (Kafka).
 */
public interface EventPublisher {

    /**
     * Publie un événement métier.
     *
     * @param aggregateType type d'agrégat (ex: "Account")
     * @param aggregateId   identifiant de l'agrégat
     * @param eventType     type d'événement (ex: "ACCOUNT_CREATED")
     * @param data          données de l'événement
     * @param metadata      métadonnées additionnelles (peut être null)
     */
    void publish(String aggregateType, String aggregateId, String eventType,
                 Map<String, Object> data, Map<String, Object> metadata);
}
