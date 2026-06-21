package com.corebanking.query.consumer;

import com.corebanking.common.enums.TransactionType;
import com.corebanking.query.entity.AccountBalance;
import com.corebanking.query.entity.TransactionHistory;
import com.corebanking.query.repository.AccountBalanceRepository;
import com.corebanking.query.repository.TransactionHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ProjectionConsumer {

    private static final int MAX_RETRIES = 3;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<String> projectionProcessor() {
        return message -> {
            int attempt = retryCount.incrementAndGet();
            log.info("Processing event, attempt {}/{}", attempt, MAX_RETRIES);
            try {
                JsonNode event = objectMapper.readTree(message);
                String eventType = event.get("eventType").asText();
                String aggregateId = event.get("aggregateId").asText();
                UUID eventId = UUID.fromString(event.get("eventId").asText());
                JsonNode data = event.get("data");

                log.debug("Projecting event: {} for aggregate {} (eventId={})", eventType, aggregateId, eventId);

                switch (eventType) {
                    case "ACCOUNT_CREATED" -> handleAccountCreated(data, aggregateId, eventId);
                    case "DEPOSIT_EXECUTED" -> handleDeposit(data, aggregateId, eventId);
                    case "WITHDRAWAL_EXECUTED" -> handleWithdrawal(data, aggregateId, eventId);
                    case "TRANSFER_SENT" -> handleTransferSent(data, aggregateId, eventId);
                    case "TRANSFER_RECEIVED" -> handleTransferReceived(data, aggregateId, eventId);
                    case "OVERDRAFT_APPLIED" -> handleOverdraft(data, aggregateId, eventId);
                    case "ACCOUNT_FROZEN", "ACCOUNT_CLOSED" -> log.info("Status event: {} for {}", eventType, aggregateId);
                    default -> log.warn("Unknown event type: {}", eventType);
                }

                retryCount.set(0);
            } catch (Exception e) {
                log.error("Failed to project event: {}", e.getMessage(), e);
                throw new RuntimeException("Projection failed", e);
            }
        };
    }

    @Transactional
    protected void handleAccountCreated(JsonNode data, String aggregateId, UUID eventId) {
        UUID accountId = UUID.fromString(aggregateId);
        String accountNumber = data.get("accountNumber").asText();

        AccountBalance balance = AccountBalance.builder()
                .accountId(accountId)
                .balance(BigDecimal.ZERO)
                .lastEventId(eventId)
                .lastUpdated(OffsetDateTime.now())
                .build();
        accountBalanceRepository.save(balance);

        log.info("Projection: AccountBalance created for {} (eventId={})", accountNumber, eventId);
    }

    @Transactional
    protected void handleDeposit(JsonNode data, String aggregateId, UUID eventId) {
        UUID accountId = UUID.fromString(aggregateId);
        BigDecimal amount = new BigDecimal(data.get("amount").asText());
        BigDecimal balanceBefore = new BigDecimal(data.get("balanceBefore").asText());
        BigDecimal balanceAfter = new BigDecimal(data.get("balanceAfter").asText());
        String reference = data.get("reference").asText();
        String description = data.has("description") && !data.get("description").isNull()
                ? data.get("description").asText() : null;

        TransactionHistory tx = TransactionHistory.builder()
                .accountId(accountId)
                .transactionType(TransactionType.DEPOSIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .reference(reference)
                .description(description)
                .eventId(eventId)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionHistoryRepository.save(tx);

        updateBalanceProjection(accountId, balanceAfter, eventId);
    }

    @Transactional
    protected void handleWithdrawal(JsonNode data, String aggregateId, UUID eventId) {
        UUID accountId = UUID.fromString(aggregateId);
        BigDecimal amount = new BigDecimal(data.get("amount").asText());
        BigDecimal balanceBefore = new BigDecimal(data.get("balanceBefore").asText());
        BigDecimal balanceAfter = new BigDecimal(data.get("balanceAfter").asText());
        String reference = data.get("reference").asText();
        String description = data.has("description") && !data.get("description").isNull()
                ? data.get("description").asText() : null;

        TransactionHistory tx = TransactionHistory.builder()
                .accountId(accountId)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .reference(reference)
                .description(description)
                .eventId(eventId)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionHistoryRepository.save(tx);

        updateBalanceProjection(accountId, balanceAfter, eventId);
    }

    @Transactional
    protected void handleTransferSent(JsonNode data, String aggregateId, UUID eventId) {
        UUID sourceAccountId = UUID.fromString(aggregateId);
        String targetAccountNumber = data.get("targetAccountNumber").asText();
        BigDecimal amount = new BigDecimal(data.get("amount").asText());
        BigDecimal balanceBefore = new BigDecimal(data.get("balanceBefore").asText());
        BigDecimal balanceAfter = new BigDecimal(data.get("balanceAfter").asText());
        String reference = data.get("reference").asText();
        String description = data.has("description") && !data.get("description").isNull()
                ? data.get("description").asText() : null;

        TransactionHistory tx = TransactionHistory.builder()
                .accountId(sourceAccountId)
                .transactionType(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .counterparty(targetAccountNumber)
                .reference(reference)
                .description(description)
                .eventId(eventId)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionHistoryRepository.save(tx);

        updateBalanceProjection(sourceAccountId, balanceAfter, eventId);
    }

    @Transactional
    protected void handleTransferReceived(JsonNode data, String aggregateId, UUID eventId) {
        UUID targetAccountId = UUID.fromString(aggregateId);
        String sourceAccountNumber = data.get("sourceAccountNumber").asText();
        BigDecimal amount = new BigDecimal(data.get("amount").asText());
        BigDecimal balanceBefore = new BigDecimal(data.get("balanceBefore").asText());
        BigDecimal balanceAfter = new BigDecimal(data.get("balanceAfter").asText());
        String reference = data.get("reference").asText();
        String description = data.has("description") && !data.get("description").isNull()
                ? data.get("description").asText() : null;

        TransactionHistory tx = TransactionHistory.builder()
                .accountId(targetAccountId)
                .transactionType(TransactionType.TRANSFER_IN)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .counterparty(sourceAccountNumber)
                .reference(reference)
                .description(description)
                .eventId(eventId)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionHistoryRepository.save(tx);

        updateBalanceProjection(targetAccountId, balanceAfter, eventId);
    }

    @Transactional
    protected void handleOverdraft(JsonNode data, String aggregateId, UUID eventId) {
        handleWithdrawal(data, aggregateId, eventId);
    }

    private void updateBalanceProjection(UUID accountId, BigDecimal balance, UUID eventId) {
        AccountBalance balanceProjection = accountBalanceRepository.findById(accountId)
                .orElse(AccountBalance.builder()
                        .accountId(accountId)
                        .balance(BigDecimal.ZERO)
                        .lastEventId(eventId)
                        .lastUpdated(OffsetDateTime.now())
                        .build());

        balanceProjection.setBalance(balance);
        balanceProjection.setLastEventId(eventId);
        balanceProjection.setLastUpdated(OffsetDateTime.now());
        accountBalanceRepository.save(balanceProjection);
    }
}
