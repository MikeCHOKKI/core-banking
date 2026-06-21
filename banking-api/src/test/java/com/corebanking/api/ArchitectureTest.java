package com.corebanking.api;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.account.service.AccountCommandService;
import com.corebanking.common.event.EventPublisher;
import com.corebanking.common.idempotency.IdempotencyService;
import com.corebanking.transfer.dto.TransferRequest;
import com.corebanking.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ArchitectureTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("banking_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.2"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private AccountCommandService accountCommandService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private IdempotencyService idempotencyService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateAccount() {
        Account account = accountCommandService.createAccount(
                "FR7612345678901234567890123",
                "John Doe",
                "john@example.com",
                "EUR",
                BigDecimal.ZERO
        );

        assertNotNull(account.getId());
        assertEquals("FR7612345678901234567890123", account.getAccountNumber());
        assertEquals(BigDecimal.ZERO.compareTo(account.getBalance()), 0);
        assertEquals("ACTIVE", account.getStatus().name());
    }

    @Test
    void shouldDepositAndUpdateBalance() {
        Account account = accountCommandService.createAccount(
                "FR7698765432109876543210987",
                "Jane Doe",
                "jane@example.com",
                "EUR",
                BigDecimal.ZERO
        );

        Account updated = accountCommandService.deposit(
                account.getId(),
                new BigDecimal("1000.00"),
                "DEP-001",
                "Initial deposit"
        );

        assertEquals(0, new BigDecimal("1000.00").compareTo(updated.getBalance()));
    }

    @Test
    void shouldWithdrawWithSufficientBalance() {
        Account account = accountCommandService.createAccount(
                "FR7655554444333322221110",
                "Alice",
                "alice@example.com",
                "EUR",
                BigDecimal.ZERO
        );

        accountCommandService.deposit(account.getId(), new BigDecimal("500.00"), "DEP-002", null);
        Account updated = accountCommandService.withdraw(account.getId(), new BigDecimal("200.00"), "WTH-001", null);

        assertEquals(0, new BigDecimal("300.00").compareTo(updated.getBalance()));
    }

    @Test
    void shouldRejectWithdrawalWithInsufficientFunds() {
        Account account = accountCommandService.createAccount(
                "FR7644443333222211110000",
                "Bob",
                "bob@example.com",
                "EUR",
                BigDecimal.ZERO
        );

        assertThrows(IllegalArgumentException.class, () ->
                accountCommandService.withdraw(account.getId(), new BigDecimal("100.00"), "WTH-002", null));
    }

    @Test
    void shouldExecuteTransferBetweenAccounts() {
        Account source = accountCommandService.createAccount(
                "FR7611111111111111111111111",
                "Source",
                "source@example.com",
                "XOF",
                BigDecimal.ZERO
        );
        Account target = accountCommandService.createAccount(
                "FR7622222222222222222222222",
                "Target",
                "target@example.com",
                "XOF",
                BigDecimal.ZERO
        );

        accountCommandService.deposit(source.getId(), new BigDecimal("5000.00"), "DEP-003", null);

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(source.getAccountNumber())
                .targetAccountNumber(target.getAccountNumber())
                .amount(new BigDecimal("2000.00"))
                .currency("XOF")
                .description("Test transfer")
                .idempotencyKey("IDEM-001-" + System.currentTimeMillis())
                .build();

        var response = transferService.executeTransfer(request);

        assertEquals("COMPLETED", response.getStatus());
        assertFalse(response.isIdempotentReplay());

        Account sourceUpdated = accountCommandService.getAccount(source.getId());
        Account targetUpdated = accountCommandService.getAccount(target.getId());

        assertEquals(0, new BigDecimal("3000.00").compareTo(sourceUpdated.getBalance()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(targetUpdated.getBalance()));
    }

    @Test
    void shouldRejectDuplicateTransferViaIdempotency() {
        Account source = accountCommandService.createAccount(
                "FR7633333333333333333333333",
                "Source2",
                "source2@example.com",
                "XOF",
                BigDecimal.ZERO
        );
        Account target = accountCommandService.createAccount(
                "FR7644444444444444444444444",
                "Target2",
                "target2@example.com",
                "XOF",
                BigDecimal.ZERO
        );

        accountCommandService.deposit(source.getId(), new BigDecimal("10000.00"), "DEP-004", null);

        String idempotencyKey = "IDEM-DUP-" + System.currentTimeMillis();

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(source.getAccountNumber())
                .targetAccountNumber(target.getAccountNumber())
                .amount(new BigDecimal("1000.00"))
                .currency("XOF")
                .description("First attempt")
                .idempotencyKey(idempotencyKey)
                .build();

        // First execution
        transferService.executeTransfer(request);

        // Second execution (should be idempotent)
        var duplicateResponse = transferService.executeTransfer(request);

        assertTrue(duplicateResponse.isIdempotentReplay());

        // Balance should not have been debited twice
        Account sourceFinal = accountCommandService.getAccount(source.getId());
        assertEquals(0, new BigDecimal("9000.00").compareTo(sourceFinal.getBalance()));
    }

    @Test
    void shouldRejectCrossCurrencyTransfer() {
        Account source = accountCommandService.createAccount(
                "FR7655555555555555555555555",
                "EUR Account",
                "eur@example.com",
                "EUR",
                BigDecimal.ZERO
        );
        Account target = accountCommandService.createAccount(
                "FR7666666666666666666666666",
                "XOF Account",
                "xof@example.com",
                "XOF",
                BigDecimal.ZERO
        );

        accountCommandService.deposit(source.getId(), new BigDecimal("1000.00"), "DEP-005", null);

        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber(source.getAccountNumber())
                .targetAccountNumber(target.getAccountNumber())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .description("Cross-currency transfer")
                .idempotencyKey("IDEM-CROSS-" + System.currentTimeMillis())
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                transferService.executeTransfer(request));
    }
}
