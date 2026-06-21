package com.corebanking.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private UUID transferId;
    private String status;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private String currency;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal targetBalanceAfter;
    private String reference;
    private OffsetDateTime timestamp;
    private boolean idempotentReplay;
}
