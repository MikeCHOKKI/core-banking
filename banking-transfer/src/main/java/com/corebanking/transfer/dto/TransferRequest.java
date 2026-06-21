package com.corebanking.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotBlank
    private String sourceAccountNumber;

    @NotBlank
    private String targetAccountNumber;

    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private String description;

    @NotBlank
    private String idempotencyKey;
}
