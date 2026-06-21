package com.corebanking.api.controller;

import com.corebanking.transfer.dto.TransferRequest;
import com.corebanking.transfer.dto.TransferResponse;
import com.corebanking.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<TransferResponse> executeTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.executeTransfer(request);
        if (response.isIdempotentReplay()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(response);
    }
}
