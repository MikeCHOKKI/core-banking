package com.corebanking.query.service;

import com.corebanking.query.entity.AccountBalance;
import com.corebanking.query.entity.TransactionHistory;
import com.corebanking.query.repository.AccountBalanceRepository;
import com.corebanking.query.repository.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public Optional<AccountBalance> getBalance(UUID accountId) {
        return accountBalanceRepository.findById(accountId);
    }

    public Page<TransactionHistory> getTransactionHistory(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return transactionHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }
}
