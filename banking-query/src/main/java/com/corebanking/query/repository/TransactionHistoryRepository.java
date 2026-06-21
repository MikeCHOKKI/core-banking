package com.corebanking.query.repository;

import com.corebanking.query.entity.TransactionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {

    Page<TransactionHistory> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
