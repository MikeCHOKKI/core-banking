package com.corebanking.transfer.idempotency;

import com.corebanking.common.idempotency.IdempotencyRecord;
import com.corebanking.common.idempotency.IdempotencyStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyService — Tests unitaires")
class RedisIdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisIdempotencyService idempotencyService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<IdempotencyRecord> recordCaptor;

    private static final String IDEMPOTENCY_KEY = "txn-001-uuid";
    private static final String REDIS_KEY = "idempotency:" + IDEMPOTENCY_KEY;
    private static final String RESOURCE_TYPE = "TRANSFER";
    private static final String RESOURCE_ID = UUID.randomUUID().toString();
    private static final String RESPONSE_BODY = "{\"status\":\"OK\"}";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ============================================================
    // isProcessed() — clé absente
    // ============================================================

    @Nested
    @DisplayName("isProcessed() — clé absente")
    class IsProcessedKeyNotExist {

        @Test
        @DisplayName("doit retourner false si la clé n'existe ni dans Redis ni en DB")
        void shouldReturnFalseWhenKeyDoesNotExist() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenReturn(null);
            when(entityManager.find(IdempotencyRecord.class, IDEMPOTENCY_KEY)).thenReturn(null);

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isFalse();
            verify(valueOperations).get(REDIS_KEY);
            verify(entityManager).find(IdempotencyRecord.class, IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("doit retourner false si la clé est null")
        void shouldReturnFalseWhenKeyIsNull() {
            // Act
            boolean result = idempotencyService.isProcessed(null);

            // Assert
            assertThat(result).isFalse();
            verifyNoInteractions(redisTemplate, entityManager);
        }

        @Test
        @DisplayName("doit retourner false si la clé est vide ou blank")
        void shouldReturnFalseWhenKeyIsBlank() {
            // Act
            boolean result = idempotencyService.isProcessed("   ");

            // Assert
            assertThat(result).isFalse();
            verifyNoInteractions(redisTemplate, entityManager);
        }
    }

    // ============================================================
    // isProcessed() — clé présente
    // ============================================================

    @Nested
    @DisplayName("isProcessed() — clé présente")
    class IsProcessedKeyExists {

        @Test
        @DisplayName("doit retourner true si la clé est dans Redis avec statut COMPLETED")
        void shouldReturnTrueWhenKeyFoundInRedis() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenReturn(IdempotencyStatus.COMPLETED.name());

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isTrue();
            verify(valueOperations).get(REDIS_KEY);
            // Pas de fallback DB si Redis répond
            verify(entityManager, never()).find(any(), any());
        }

        @Test
        @DisplayName("doit retourner false si la clé est dans Redis avec statut FAILED")
        void shouldReturnFalseWhenKeyFoundWithFailedStatus() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenReturn(IdempotencyStatus.FAILED.name());

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("doit retourner true si la clé est en DB (fallback) et repopuler Redis")
        void shouldReturnTrueWhenKeyFoundInDbAndRepopulateRedis() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenReturn(null);
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(IDEMPOTENCY_KEY)
                    .status(IdempotencyStatus.COMPLETED)
                    .resourceType(RESOURCE_TYPE)
                    .build();
            when(entityManager.find(IdempotencyRecord.class, IDEMPOTENCY_KEY)).thenReturn(record);

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isTrue();
            // Vérifier que le cache Redis est repopulé
            verify(valueOperations).set(
                    REDIS_KEY,
                    IdempotencyStatus.COMPLETED.name(),
                    86400L,
                    TimeUnit.SECONDS
            );
        }
    }

    // ============================================================
    // isProcessed() — fallback Redis
    // ============================================================

    @Nested
    @DisplayName("isProcessed() — fallback Redis")
    class IsProcessedRedisFallback {

        @Test
        @DisplayName("doit utiliser le fallback DB si Redis est indisponible")
        void shouldFallbackToDbWhenRedisUnavailable() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenThrow(new DataAccessException("Redis down") {});
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(IDEMPOTENCY_KEY)
                    .status(IdempotencyStatus.COMPLETED)
                    .resourceType(RESOURCE_TYPE)
                    .build();
            when(entityManager.find(IdempotencyRecord.class, IDEMPOTENCY_KEY)).thenReturn(record);

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isTrue();
            verify(entityManager).find(IdempotencyRecord.class, IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("doit retourner false si Redis ET DB sont indisponibles")
        void shouldReturnFalseWhenBothRedisAndDbFail() {
            // Arrange
            when(valueOperations.get(REDIS_KEY)).thenThrow(new DataAccessException("Redis down") {});
            when(entityManager.find(IdempotencyRecord.class, IDEMPOTENCY_KEY)).thenReturn(null);

            // Act
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isFalse();
        }
    }

    // ============================================================
    // markCompleted()
    // ============================================================

    @Nested
    @DisplayName("markCompleted()")
    class MarkCompletedTests {

        @Test
        @DisplayName("doit marquer la clé dans Redis et persister en DB")
        void shouldMarkInRedisAndPersistInDb() {
            // Act
            idempotencyService.markCompleted(IDEMPOTENCY_KEY, RESOURCE_TYPE, RESOURCE_ID, RESPONSE_BODY);

            // Assert
            // Vérifie Redis
            verify(valueOperations).set(
                    eq(REDIS_KEY),
                    eq(IdempotencyStatus.COMPLETED.name()),
                    eq(86400L),
                    eq(TimeUnit.SECONDS)
            );

            // Vérifie DB
            verify(entityManager).persist(recordCaptor.capture());
            IdempotencyRecord captured = recordCaptor.getValue();
            assertThat(captured.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
            assertThat(captured.getResourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(captured.getResourceId()).isEqualTo(UUID.fromString(RESOURCE_ID));
            assertThat(captured.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
            assertThat(captured.getResponseBody()).isEqualTo(RESPONSE_BODY);
            assertThat(captured.getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("ne doit rien faire si la clé est null")
        void shouldDoNothingWhenKeyIsNull() {
            // Act
            idempotencyService.markCompleted(null, RESOURCE_TYPE, RESOURCE_ID, RESPONSE_BODY);

            // Assert
            verifyNoInteractions(redisTemplate, entityManager);
        }

        @Test
        @DisplayName("doit persister en DB même si Redis échoue")
        void shouldPersistInDbEvenWhenRedisFails() {
            // Arrange — ValueOperations.set() retourne void, on utilise doThrow
            doThrow(new DataAccessException("Redis down") {})
                    .when(valueOperations)
                    .set(eq(REDIS_KEY), eq(IdempotencyStatus.COMPLETED.name()), eq(86400L), eq(TimeUnit.SECONDS));

            // Act
            idempotencyService.markCompleted(IDEMPOTENCY_KEY, RESOURCE_TYPE, RESOURCE_ID, RESPONSE_BODY);

            // Assert
            // La persistence DB doit avoir lieu malgré l'erreur Redis
            verify(entityManager).persist(any(IdempotencyRecord.class));
        }
    }

    // ============================================================
    // isProcessed() après markCompleted() — workflow complet
    // ============================================================

    @Nested
    @DisplayName("Workflow complet : markCompleted() puis isProcessed()")
    class FullWorkflowTests {

        @Test
        @DisplayName("doit retourner true après avoir marqué la clé comme complétée")
        void shouldReturnTrueAfterMarkCompleted() {
            // Arrange — markCompleted
            idempotencyService.markCompleted(IDEMPOTENCY_KEY, RESOURCE_TYPE, RESOURCE_ID, RESPONSE_BODY);

            // Simule le comportement après marquage
            when(valueOperations.get(REDIS_KEY)).thenReturn(IdempotencyStatus.COMPLETED.name());

            // Act — isProcessed
            boolean result = idempotencyService.isProcessed(IDEMPOTENCY_KEY);

            // Assert
            assertThat(result).isTrue();
            verify(valueOperations).get(REDIS_KEY);
        }
    }

    // ============================================================
    // markFailed()
    // ============================================================

    @Nested
    @DisplayName("markFailed()")
    class MarkFailedTests {

        @Test
        @DisplayName("doit marquer la clé comme FAILED dans Redis et DB")
        void shouldMarkFailedInRedisAndDb() {
            // Arrange
            String errorBody = "{\"error\":\"Insufficient funds\"}";

            // Act
            idempotencyService.markFailed(IDEMPOTENCY_KEY, RESOURCE_TYPE, errorBody);

            // Assert — Redis
            verify(valueOperations).set(
                    eq(REDIS_KEY),
                    eq(IdempotencyStatus.FAILED.name()),
                    eq(86400L),
                    eq(TimeUnit.SECONDS)
            );

            // Assert — DB
            verify(entityManager).persist(recordCaptor.capture());
            IdempotencyRecord captured = recordCaptor.getValue();
            assertThat(captured.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
            assertThat(captured.getResponseBody()).isEqualTo(errorBody);
        }

        @Test
        @DisplayName("ne doit rien faire si la clé est null")
        void shouldDoNothingWhenKeyIsNull() {
            // Act
            idempotencyService.markFailed(null, RESOURCE_TYPE, "error");

            // Assert
            verifyNoInteractions(redisTemplate, entityManager);
        }
    }

    // ============================================================
    // setExpiry()
    // ============================================================

    @Nested
    @DisplayName("setExpiry()")
    class SetExpiryTests {

        @Test
        @DisplayName("doit modifier le TTL sur Redis")
        void shouldSetExpiryOnRedis() {
            // Act
            idempotencyService.setExpiry(IDEMPOTENCY_KEY, 3600L);

            // Assert
            verify(redisTemplate).expire(REDIS_KEY, 3600L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("ne doit pas échouer si Redis est indisponible")
        void shouldNotThrowWhenRedisUnavailable() {
            // Arrange
            doThrow(new DataAccessException("Redis down") {})
                    .when(redisTemplate).expire(anyString(), anyLong(), any());

            // Act — ne doit pas lever d'exception
            idempotencyService.setExpiry(IDEMPOTENCY_KEY, 3600L);

            // Assert
            verify(redisTemplate).expire(REDIS_KEY, 3600L, TimeUnit.SECONDS);
        }
    }
}
