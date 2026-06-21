package com.corebanking.api.controller;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.account.service.AccountCommandService;
import com.corebanking.api.TestMvcConfig;
import com.corebanking.api.service.AuthService;
import com.corebanking.common.enums.AccountStatus;
import com.corebanking.query.service.AccountQueryService;
import com.corebanking.transfer.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestMvcConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AccountController — Tests d'intégration")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Services de AccountController
    @MockitoBean
    private AccountCommandService accountCommandService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private AccountRepository accountRepository;

    // Services des autres contrôleurs (chargés par @RestController scan)
    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TransferService transferService;

    private final UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final String accountNumber = "FR7612345678901234567890123";
    private final String ownerName = "Jean Dupont";
    private final String ownerEmail = "jean.dupont@example.com";
    private final String currency = "EUR";
    private final BigDecimal balance = new BigDecimal("1500.00");
    private final BigDecimal overdraftLimit = BigDecimal.ZERO;

    private Account createActiveAccount() {
        Account account = new Account();
        account.setId(accountId);
        account.setAccountNumber(accountNumber);
        account.setOwnerName(ownerName);
        account.setOwnerEmail(ownerEmail);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setOverdraftLimit(overdraftLimit);
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0L);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        return account;
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/accounts
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts — retourne 200 avec la liste paginée")
    void listAccounts_shouldReturn200WithPaginatedList() throws Exception {
        Account account = createActiveAccount();
        Page<Account> page = new PageImpl<>(List.of(account));

        when(accountRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/accounts")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(accountId.toString())))
                .andExpect(jsonPath("$[0].accountNumber", is(accountNumber)))
                .andExpect(jsonPath("$[0].ownerName", is(ownerName)))
                .andExpect(jsonPath("$[0].currency", is(currency)))
                .andExpect(jsonPath("$[0].balance", is(balance.doubleValue())))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/accounts/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts/{id} — retourne 200 avec le compte")
    void getAccount_shouldReturn200WhenFound() throws Exception {
        Account account = createActiveAccount();

        when(accountCommandService.getAccount(accountId)).thenReturn(account);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.accountNumber", is(accountNumber)))
                .andExpect(jsonPath("$.ownerName", is(ownerName)))
                .andExpect(jsonPath("$.currency", is(currency)))
                .andExpect(jsonPath("$.balance", is(balance.doubleValue())))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} — retourne 404 si le compte n'existe pas")
    void getAccount_shouldReturn404WhenNotFound() throws Exception {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        when(accountCommandService.getAccount(unknownId))
                .thenThrow(new IllegalArgumentException("Account not found: " + unknownId));

        mockMvc.perform(get("/api/v1/accounts/{id}", unknownId))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/accounts
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts — retourne 201 avec le compte créé")
    void createAccount_shouldReturn201WhenValid() throws Exception {
        Account account = createActiveAccount();

        when(accountCommandService.createAccount(
                eq(accountNumber), eq(ownerName), eq(ownerEmail),
                eq(currency), any(BigDecimal.class)))
                .thenReturn(account);

        String body = """
                {
                    "accountNumber": "%s",
                    "ownerName": "%s",
                    "ownerEmail": "%s",
                    "currency": "%s",
                    "overdraftLimit": %s
                }
                """.formatted(accountNumber, ownerName, ownerEmail, currency, overdraftLimit);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.accountNumber", is(accountNumber)))
                .andExpect(jsonPath("$.ownerName", is(ownerName)))
                .andExpect(jsonPath("$.currency", is(currency)))
                .andExpect(jsonPath("$.balance", is(balance.doubleValue())))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("POST /api/v1/accounts — retourne 400 si l'email est invalide")
    void createAccount_shouldReturn400WhenEmailInvalid() throws Exception {
        String body = """
                {
                    "accountNumber": "%s",
                    "ownerName": "%s",
                    "ownerEmail": "pas-un-email",
                    "currency": "%s",
                    "overdraftLimit": %s
                }
                """.formatted(accountNumber, ownerName, currency, overdraftLimit);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/accounts/{id}/deposit
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/deposit — retourne 200")
    void deposit_shouldReturn200WhenValid() throws Exception {
        BigDecimal depositAmount = new BigDecimal("500.00");
        String reference = "DEP-001";
        String description = "Dépôt initial";
        Account updatedAccount = createActiveAccount();
        updatedAccount.setBalance(balance.add(depositAmount));

        when(accountCommandService.deposit(
                eq(accountId), eq(depositAmount), eq(reference), eq(description)))
                .thenReturn(updatedAccount);

        String body = """
                {
                    "amount": %s,
                    "reference": "%s",
                    "description": "%s"
                }
                """.formatted(depositAmount, reference, description);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.balance", is(2000.0)));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/accounts/{id}/withdraw
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/withdraw — retourne 200")
    void withdraw_shouldReturn200WhenValid() throws Exception {
        BigDecimal withdrawAmount = new BigDecimal("200.00");
        String reference = "WTH-001";
        String description = "Retrait DAB";
        Account updatedAccount = createActiveAccount();
        updatedAccount.setBalance(balance.subtract(withdrawAmount));

        when(accountCommandService.withdraw(
                eq(accountId), eq(withdrawAmount), eq(reference), eq(description)))
                .thenReturn(updatedAccount);

        String body = """
                {
                    "amount": %s,
                    "reference": "%s",
                    "description": "%s"
                }
                """.formatted(withdrawAmount, reference, description);

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.balance", is(1300.0)));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/accounts/{id}/freeze
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/freeze — retourne 200")
    void freezeAccount_shouldReturn200() throws Exception {
        Account frozenAccount = createActiveAccount();
        frozenAccount.setStatus(AccountStatus.FROZEN);

        when(accountCommandService.freezeAccount(accountId)).thenReturn(frozenAccount);

        mockMvc.perform(post("/api/v1/accounts/{id}/freeze", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.status", is("FROZEN")));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/accounts/{id}/close
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/close — retourne 200")
    void closeAccount_shouldReturn200() throws Exception {
        Account closedAccount = createActiveAccount();
        closedAccount.setBalance(BigDecimal.ZERO);
        closedAccount.setStatus(AccountStatus.CLOSED);

        when(accountCommandService.closeAccount(accountId)).thenReturn(closedAccount);

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.status", is("CLOSED")));
    }
}
