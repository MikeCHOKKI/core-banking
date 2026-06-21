package com.corebanking.api.controller;

import com.corebanking.account.entity.Account;
import com.corebanking.account.service.AccountCommandService;
import com.corebanking.query.entity.AccountBalance;
import com.corebanking.query.entity.TransactionHistory;
import com.corebanking.query.service.AccountQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountCommandService accountCommandService;
    private final AccountQueryService accountQueryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountCommandService.createAccount(
                request.getAccountNumber(),
                request.getOwnerName(),
                request.getOwnerEmail(),
                request.getCurrency(),
                request.getOverdraftLimit()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        try {
            Account account = accountCommandService.getAccount(id);
            return ResponseEntity.ok(toResponse(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/by-number/{accountNumber}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        try {
            Account account = accountCommandService.getAccountByNumber(accountNumber);
            return ResponseEntity.ok(toResponse(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/deposit")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        Account account = accountCommandService.deposit(
                id, request.getAmount(), request.getReference(), request.getDescription());
        return ResponseEntity.ok(toResponse(account));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        Account account = accountCommandService.withdraw(
                id, request.getAmount(), request.getReference(), request.getDescription());
        return ResponseEntity.ok(toResponse(account));
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        return accountQueryService.getBalance(id)
                .map(b -> ResponseEntity.ok(new BalanceResponse(b.getAccountId(), b.getBalance(), b.getLastUpdated())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<Page<TransactionHistory>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountQueryService.getTransactionHistory(id, page, size));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .overdraftLimit(account.getOverdraftLimit())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .build();
    }

    // --- DTOs ---

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AccountResponse {
        private UUID id;
        private String accountNumber;
        private String ownerName;
        private String currency;
        private BigDecimal balance;
        private BigDecimal overdraftLimit;
        private String status;
        private OffsetDateTime createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateAccountRequest {
        @NotBlank private String accountNumber;
        @NotBlank private String ownerName;
        @NotBlank private String ownerEmail;
        private String currency;
        private BigDecimal overdraftLimit;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TransactionRequest {
        @Positive private BigDecimal amount;
        @NotBlank private String reference;
        private String description;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BalanceResponse {
        private UUID accountId;
        private BigDecimal balance;
        private OffsetDateTime lastUpdated;
    }
}
