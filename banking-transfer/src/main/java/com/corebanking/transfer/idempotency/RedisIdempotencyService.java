package com.corebanking.transfer.idempotency;

import com.corebanking.common.idempotency.IdempotencyRecord;
import com.corebanking.common.idempotency.IdempotencyService;
import com.corebanking.common.idempotency.IdempotencyStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyService implements IdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long DEFAULT_TTL_SECONDS = 86400; // 24h

    private final StringRedisTemplate redisTemplate;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isProcessed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return false;

        try {
            // 1. Check Redis first (fast path)
            String status = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
            if (status != null) {
                log.debug("Idempotency key {} found in Redis: {}", idempotencyKey, status);
                return IdempotencyStatus.COMPLETED.name().equals(status);
            }

            // 2. Fallback to PostgreSQL
            IdempotencyRecord record = entityManager.find(IdempotencyRecord.class, idempotencyKey);
            if (record != null) {
                // Re-populate Redis cache
                redisTemplate.opsForValue().set(
                        IDEMPOTENCY_PREFIX + idempotencyKey,
                        record.getStatus().name(),
                        DEFAULT_TTL_SECONDS,
                        TimeUnit.SECONDS
                );
                return record.getStatus() == IdempotencyStatus.COMPLETED;
            }

            return false;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, falling back to DB for idempotency check: {}", e.getMessage());
            // Fallback direct DB
            IdempotencyRecord record = entityManager.find(IdempotencyRecord.class, idempotencyKey);
            return record != null && record.getStatus() == IdempotencyStatus.COMPLETED;
        }
    }

    @Override
    @Transactional
    public void markCompleted(String idempotencyKey, String resourceType, String resourceId, String responseBody) {
        if (idempotencyKey == null) return;

        try {
            // Redis
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_PREFIX + idempotencyKey,
                    IdempotencyStatus.COMPLETED.name(),
                    DEFAULT_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, persisting idempotency to DB only: {}", e.getMessage());
        }

        // DB persistence (always)
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .resourceType(resourceType)
                .resourceId(resourceId != null ? UUID.fromString(resourceId) : null)
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(responseBody)
                .expiresAt(OffsetDateTime.now().plusSeconds(DEFAULT_TTL_SECONDS))
                .createdAt(OffsetDateTime.now())
                .build();

        entityManager.persist(record);
        log.info("Idempotency key {} marked as COMPLETED", idempotencyKey);
    }

    @Override
    @Transactional
    public void markFailed(String idempotencyKey, String resourceType, String errorBody) {
        if (idempotencyKey == null) return;

        try {
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_PREFIX + idempotencyKey,
                    IdempotencyStatus.FAILED.name(),
                    DEFAULT_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (DataAccessException e) {
            log.warn("Redis unavailable: {}", e.getMessage());
        }

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .resourceType(resourceType)
                .status(IdempotencyStatus.FAILED)
                .responseBody(errorBody)
                .expiresAt(OffsetDateTime.now().plusSeconds(DEFAULT_TTL_SECONDS))
                .createdAt(OffsetDateTime.now())
                .build();

        entityManager.persist(record);
        log.info("Idempotency key {} marked as FAILED", idempotencyKey);
    }

    @Override
    public void setExpiry(String idempotencyKey, long ttlSeconds) {
        try {
            redisTemplate.expire(IDEMPOTENCY_PREFIX + idempotencyKey, ttlSeconds, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable: {}", e.getMessage());
        }
    }
}
