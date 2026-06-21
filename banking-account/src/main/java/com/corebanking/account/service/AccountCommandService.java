package com.corebanking.account.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.common.enums.AccountStatus;
import com.corebanking.common.event.EventPublisher;
import com.corebanking.common.event.EventType;
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
public class AccountCommandService {

    private final AccountRepository accountRepository;
    private final EventPublisher eventPublisher;

    /**
     * Crée un nouveau compte bancaire.
     */
    @Transactional
    public Account createAccount(String accountNumber, String ownerName, String ownerEmail,
                                  String currency, BigDecimal overdraftLimit) {
        // Validation
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new IllegalArgumentException("Account number already exists: " + accountNumber);
        }

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ownerName(ownerName)
                .ownerEmail(ownerEmail)
                .currency(currency != null ? currency : "XOF")
                .balance(BigDecimal.ZERO)
                .overdraftLimit(overdraftLimit != null ? overdraftLimit : BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        account = accountRepository.save(account);

        // Publier l'événement
        Map<String, Object> data = new HashMap<>();
        data.put("accountId", account.getId().toString());
        data.put("accountNumber", account.getAccountNumber());
        data.put("ownerName", account.getOwnerName());
        data.put("currency", account.getCurrency());
        data.put("overdraftLimit", account.getOverdraftLimit().toString());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ownerEmail", account.getOwnerEmail());

        eventPublisher.publish(
                "Account",
                account.getId().toString(),
                EventType.ACCOUNT_CREATED.name(),
                data,
                metadata
        );

        log.info("Account created: {} ({})", account.getAccountNumber(), account.getId());
        return account;
    }

    /**
     * Effectue un dépôt sur un compte.
     * Utilise le verrou optimiste @Version pour la concurrence.
     */
    @Transactional
    public Account deposit(UUID accountId, BigDecimal amount, String reference, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getStatus());
        }

        BigDecimal balanceBefore = account.getBalance();
        account.setBalance(account.getBalance().add(amount));

        try {
            account = accountRepository.save(account);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure on deposit for account {}", accountId);
            throw new OptimisticLockException("Concurrent modification detected, retry transaction", e);
        }

        // Publier l'événement
        Map<String, Object> data = new HashMap<>();
        data.put("accountId", accountId.toString());
        data.put("amount", amount.toString());
        data.put("balanceBefore", balanceBefore.toString());
        data.put("balanceAfter", account.getBalance().toString());
        data.put("reference", reference);
        data.put("description", description);

        eventPublisher.publish(
                "Account",
                accountId.toString(),
                EventType.DEPOSIT_EXECUTED.name(),
                data,
                null
        );

        log.info("Deposit of {} on account {}: balance {} → {} (ref: {})",
                amount, accountId, balanceBefore, account.getBalance(), reference);
        return account;
    }

    /**
     * Effectue un retrait d'un compte.
     * Vérifie le solde + découvert autorisé.
     */
    @Transactional
    public Account withdraw(UUID accountId, BigDecimal amount, String reference, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getStatus());
        }

        BigDecimal availableBalance = account.getBalance().add(account.getOverdraftLimit());
        if (amount.compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException("Insufficient funds: available=" + availableBalance
                    + ", requested=" + amount);
        }

        BigDecimal balanceBefore = account.getBalance();
        account.setBalance(account.getBalance().subtract(amount));

        try {
            account = accountRepository.save(account);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure on withdrawal for account {}", accountId);
            throw new OptimisticLockException("Concurrent modification detected, retry transaction", e);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("accountId", accountId.toString());
        data.put("amount", amount.toString());
        data.put("balanceBefore", balanceBefore.toString());
        data.put("balanceAfter", account.getBalance().toString());
        data.put("reference", reference);
        data.put("description", description);

        EventType eventType = account.getBalance().compareTo(BigDecimal.ZERO) < 0
                ? EventType.OVERDRAFT_APPLIED
                : EventType.WITHDRAWAL_EXECUTED;

        eventPublisher.publish(
                "Account",
                accountId.toString(),
                eventType.name(),
                data,
                null
        );

        log.info("Withdrawal of {} from account {}: balance {} → {} (ref: {})",
                amount, accountId, balanceBefore, account.getBalance(), reference);
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }
}
