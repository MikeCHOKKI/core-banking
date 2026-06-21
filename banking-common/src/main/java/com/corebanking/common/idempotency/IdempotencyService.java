package com.corebanking.common.idempotency;

public interface IdempotencyService {
    boolean isProcessed(String idempotencyKey);
    void markCompleted(String idempotencyKey, String resourceType, String resourceId, String responseBody);
    void markFailed(String idempotencyKey, String resourceType, String errorBody);
    void setExpiry(String idempotencyKey, long ttlSeconds);
}
