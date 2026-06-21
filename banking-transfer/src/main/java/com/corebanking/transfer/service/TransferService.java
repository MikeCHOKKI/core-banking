package com.corebanking.transfer.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.common.enums.AccountStatus;
import com.corebanking.common.event.EventPublisher;
import com.corebanking.common.event.EventType;
import com.corebanking.common.idempotency.IdempotencyService;
import com.corebanking.transfer.dto.TransferRequest;
import com.corebanking.transfer.dto.TransferResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;
    private final EventPublisher eventPublisher;

    @Transactional
    public TransferResponse executeTransfer(TransferRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // ==========================================
        // 1. IDEMPOTENCE CHECK — Déduplication
        // ==========================================
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Idempotent request detected, returning cached result: {}", idempotencyKey);
            // En production, on retournerait le résultat original stocké
            return TransferResponse.builder()
                    .status("ALREADY_PROCESSED")
                    .reference(idempotencyKey)
                    .timestamp(OffsetDateTime.now())
                    .idempotentReplay(true)
                    .build();
        }

        // ==========================================
        // 2. PESSIMISTIC LOCKS — Verrous ACID
        // ==========================================
        Account sourceAccount = accountRepository
                .findByAccountNumberWithPessimisticLock(request.getSourceAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source account not found: " + request.getSourceAccountNumber()));

        Account targetAccount = accountRepository
                .findByAccountNumberWithPessimisticLock(request.getTargetAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target account not found: " + request.getTargetAccountNumber()));

        // ==========================================
        // 3. VALIDATIONS MÉTIER
        // ==========================================
        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account is not active: " + sourceAccount.getStatus());
        }
        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Target account is not active: " + targetAccount.getStatus());
        }
        if (!sourceAccount.getCurrency().equals(request.getCurrency())) {
            throw new IllegalArgumentException("Source account currency mismatch: "
                    + sourceAccount.getCurrency() + " vs " + request.getCurrency());
        }
        if (!targetAccount.getCurrency().equals(request.getCurrency())) {
            throw new IllegalArgumentException("Target account currency mismatch: "
                    + targetAccount.getCurrency() + " vs " + request.getCurrency());
        }

        BigDecimal availableBalance = sourceAccount.getBalance().add(sourceAccount.getOverdraftLimit());
        if (request.getAmount().compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException("Insufficient funds: available=" + availableBalance
                    + ", requested=" + request.getAmount());
        }

        // ==========================================
        // 4. EXÉCUTION ATOMIQUE (Débit + Crédit)
        // ==========================================
        BigDecimal sourceBalanceBefore = sourceAccount.getBalance();
        BigDecimal targetBalanceBefore = targetAccount.getBalance();

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        targetAccount.setBalance(targetAccount.getBalance().add(request.getAmount()));

        try {
            sourceAccount = accountRepository.save(sourceAccount);
            targetAccount = accountRepository.save(targetAccount);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure during transfer from {} to {}",
                    request.getSourceAccountNumber(), request.getTargetAccountNumber());
            throw new OptimisticLockException("Concurrent modification detected, retry transaction", e);
        }

        // ==========================================
        // 5. PUBLICATION ÉVÉNEMENTS (Event Sourcing)
        // ==========================================
        String reference = generateReference(idempotencyKey);

        // Événement TRANSFER_SENT (source)
        Map<String, Object> sentData = new HashMap<>();
        sentData.put("sourceAccountId", sourceAccount.getId().toString());
        sentData.put("targetAccountId", targetAccount.getId().toString());
        sentData.put("sourceAccountNumber", sourceAccount.getAccountNumber());
        sentData.put("targetAccountNumber", targetAccount.getAccountNumber());
        sentData.put("amount", request.getAmount().toString());
        sentData.put("currency", request.getCurrency());
        sentData.put("balanceBefore", sourceBalanceBefore.toString());
        sentData.put("balanceAfter", sourceAccount.getBalance().toString());
        sentData.put("reference", reference);
        sentData.put("description", request.getDescription());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("idempotencyKey", idempotencyKey);

        eventPublisher.publishWithIdempotency(
                "Account",
                sourceAccount.getId().toString(),
                EventType.TRANSFER_SENT.name(),
                sentData,
                metadata,
                idempotencyKey + "-sent"
        );

        // Événement TRANSFER_RECEIVED (cible)
        Map<String, Object> receivedData = new HashMap<>();
        receivedData.put("sourceAccountId", sourceAccount.getId().toString());
        receivedData.put("targetAccountId", targetAccount.getId().toString());
        receivedData.put("sourceAccountNumber", sourceAccount.getAccountNumber());
        receivedData.put("targetAccountNumber", targetAccount.getAccountNumber());
        receivedData.put("amount", request.getAmount().toString());
        receivedData.put("currency", request.getCurrency());
        receivedData.put("balanceBefore", targetBalanceBefore.toString());
        receivedData.put("balanceAfter", targetAccount.getBalance().toString());
        receivedData.put("reference", reference);
        receivedData.put("description", request.getDescription());

        eventPublisher.publishWithIdempotency(
                "Account",
                targetAccount.getId().toString(),
                EventType.TRANSFER_RECEIVED.name(),
                receivedData,
                metadata,
                idempotencyKey + "-received"
        );

        // ==========================================
        // 6. MARK IDEMPOTENT — Clé marquée complétée
        // ==========================================
        String responseBody = "{\"reference\":\"" + reference + "\"}";
        idempotencyService.markCompleted(idempotencyKey, "TRANSFER",
                sourceAccount.getId().toString(), responseBody);

        log.info("Transfer executed: {} {} from {} to {} (ref: {})",
                request.getAmount(), request.getCurrency(),
                sourceAccount.getAccountNumber(), targetAccount.getAccountNumber(),
                reference);

        return TransferResponse.builder()
                .transferId(sourceAccount.getId())
                .status("COMPLETED")
                .sourceAccountNumber(sourceAccount.getAccountNumber())
                .targetAccountNumber(targetAccount.getAccountNumber())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .sourceBalanceAfter(sourceAccount.getBalance())
                .targetBalanceAfter(targetAccount.getBalance())
                .reference(reference)
                .timestamp(OffsetDateTime.now())
                .idempotentReplay(false)
                .build();
    }

    private String generateReference(String idempotencyKey) {
        return "TRF-" + idempotencyKey.substring(0, Math.min(12, idempotencyKey.length())).toUpperCase()
                + "-" + System.currentTimeMillis() % 100000;
    }
}
