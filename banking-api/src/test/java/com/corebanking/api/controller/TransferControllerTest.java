package com.corebanking.api.controller;

import com.corebanking.account.repository.AccountRepository;
import com.corebanking.account.service.AccountCommandService;
import com.corebanking.api.TestMvcConfig;
import com.corebanking.api.service.AuthService;
import com.corebanking.query.service.AccountQueryService;
import com.corebanking.transfer.dto.TransferRequest;
import com.corebanking.transfer.dto.TransferResponse;
import com.corebanking.transfer.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestMvcConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TransferController — Tests d'intégration")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Service de TransferController
    @MockitoBean
    private TransferService transferService;

    // Services des autres contrôleurs (chargés par @RestController scan)
    @MockitoBean
    private AccountCommandService accountCommandService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private AuthService authService;

    private final String sourceAccountNumber = "FR7612345678901234567890123";
    private final String targetAccountNumber = "FR7698765432109876543210987";
    private final BigDecimal amount = new BigDecimal("500.00");
    private final String currency = "EUR";
    private final String idempotencyKey = "IDEM-UNIQUE-001";
    private final String reference = "TRF-IDEM-001-12345";

    // -----------------------------------------------------------------------
    // POST /api/v1/transfers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/transfers — retourne 200 pour un virement valide")
    void executeTransfer_shouldReturn200WhenValid() throws Exception {
        TransferResponse response = TransferResponse.builder()
                .transferId(UUID.randomUUID())
                .status("COMPLETED")
                .sourceAccountNumber(sourceAccountNumber)
                .targetAccountNumber(targetAccountNumber)
                .amount(amount)
                .currency(currency)
                .sourceBalanceAfter(new BigDecimal("1000.00"))
                .targetBalanceAfter(new BigDecimal("2500.00"))
                .reference(reference)
                .timestamp(OffsetDateTime.now())
                .idempotentReplay(false)
                .build();

        when(transferService.executeTransfer(any(TransferRequest.class))).thenReturn(response);

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(sourceAccountNumber)
                .targetAccountNumber(targetAccountNumber)
                .amount(amount)
                .currency(currency)
                .description("Virement test")
                .idempotencyKey(idempotencyKey)
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serialize(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.sourceAccountNumber", is(sourceAccountNumber)))
                .andExpect(jsonPath("$.targetAccountNumber", is(targetAccountNumber)))
                .andExpect(jsonPath("$.amount", is(amount.doubleValue())))
                .andExpect(jsonPath("$.currency", is(currency)))
                .andExpect(jsonPath("$.reference", is(reference)))
                .andExpect(jsonPath("$.idempotentReplay", is(false)));
    }

    @Test
    @DisplayName("POST /api/v1/transfers — retourne 400 si le montant est négatif")
    void executeTransfer_shouldReturn400WhenAmountNegative() throws Exception {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(sourceAccountNumber)
                .targetAccountNumber(targetAccountNumber)
                .amount(new BigDecimal("-100.00"))
                .currency(currency)
                .description("Montant négatif")
                .idempotencyKey(idempotencyKey)
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serialize(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transfers — retourne 409 si idempotencyKey est en double")
    void executeTransfer_shouldReturn409WhenIdempotencyKeyDuplicate() throws Exception {
        when(transferService.executeTransfer(any(TransferRequest.class)))
                .thenThrow(new IllegalStateException("ALREADY_PROCESSED"));

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(sourceAccountNumber)
                .targetAccountNumber(targetAccountNumber)
                .amount(amount)
                .currency(currency)
                .description("Double virement")
                .idempotencyKey("IDEM-DUP-001")
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serialize(request)))
                .andExpect(status().isConflict());
    }

    private static String serialize(TransferRequest request) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
