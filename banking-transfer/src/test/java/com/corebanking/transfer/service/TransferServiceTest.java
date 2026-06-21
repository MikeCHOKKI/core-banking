package com.corebanking.transfer.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.common.enums.AccountStatus;
import com.corebanking.common.event.EventPublisher;
import com.corebanking.common.event.EventType;
import com.corebanking.common.idempotency.IdempotencyService;
import com.corebanking.transfer.dto.TransferRequest;
import com.corebanking.transfer.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService — Tests unitaires")
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TransferService transferService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    // --- Données communes ---
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final String SOURCE_ACCOUNT = "FR761234500001";
    private static final String TARGET_ACCOUNT = "FR761234500002";
    private static final String CURRENCY = "EUR";
    private static final String IDEMPOTENCY_KEY = "idemp-001-abc";
    private static final BigDecimal SOURCE_BALANCE = BigDecimal.valueOf(1000);
    private static final BigDecimal TARGET_BALANCE = BigDecimal.valueOf(500);
    private static final BigDecimal TRANSFER_AMOUNT = BigDecimal.valueOf(300);
    private static final BigDecimal OVERDRAFT_LIMIT = BigDecimal.ZERO;

    private Account sourceAccount;
    private Account targetAccount;
    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        sourceAccount = Account.builder()
                .id(SOURCE_ID)
                .accountNumber(SOURCE_ACCOUNT)
                .ownerName("Alice")
                .ownerEmail("alice@example.com")
                .currency(CURRENCY)
                .balance(SOURCE_BALANCE)
                .overdraftLimit(OVERDRAFT_LIMIT)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        targetAccount = Account.builder()
                .id(TARGET_ID)
                .accountNumber(TARGET_ACCOUNT)
                .ownerName("Bob")
                .ownerEmail("bob@example.com")
                .currency(CURRENCY)
                .balance(TARGET_BALANCE)
                .overdraftLimit(OVERDRAFT_LIMIT)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        validRequest = TransferRequest.builder()
                .sourceAccountNumber(SOURCE_ACCOUNT)
                .targetAccountNumber(TARGET_ACCOUNT)
                .amount(TRANSFER_AMOUNT)
                .currency(CURRENCY)
                .description("Virement test")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    // ============================================================
    // executeTransfer() — succès nominal
    // ============================================================

    @Nested
    @DisplayName("executeTransfer() — succès")
    class SuccessTests {

        @Test
        @DisplayName("doit exécuter un virement avec débit/crédit correct et publier les événements")
        void shouldExecuteTransferSuccessfully() {
            // Arrange
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            TransferResponse response = transferService.executeTransfer(validRequest);

            // Assert
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.isIdempotentReplay()).isFalse();
            assertThat(response.getReference()).startsWith("TRF-");
            assertThat(response.getSourceAccountNumber()).isEqualTo(SOURCE_ACCOUNT);
            assertThat(response.getTargetAccountNumber()).isEqualTo(TARGET_ACCOUNT);
            assertThat(response.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
            assertThat(response.getCurrency()).isEqualTo(CURRENCY);

            // Vérifier le débit de la source
            assertThat(response.getSourceBalanceAfter())
                    .isEqualByComparingTo(SOURCE_BALANCE.subtract(TRANSFER_AMOUNT)); // 1000 - 300 = 700

            // Vérifier le crédit de la cible
            assertThat(response.getTargetBalanceAfter())
                    .isEqualByComparingTo(TARGET_BALANCE.add(TRANSFER_AMOUNT)); // 500 + 300 = 800

            // Vérifier les appels aux événements
            verify(eventPublisher).publishWithIdempotency(
                    eq("Account"),
                    eq(SOURCE_ID.toString()),
                    eq(EventType.TRANSFER_SENT.name()),
                    any(Map.class),
                    any(Map.class),
                    eq(IDEMPOTENCY_KEY + "-sent")
            );
            verify(eventPublisher).publishWithIdempotency(
                    eq("Account"),
                    eq(TARGET_ID.toString()),
                    eq(EventType.TRANSFER_RECEIVED.name()),
                    any(Map.class),
                    any(Map.class),
                    eq(IDEMPOTENCY_KEY + "-received")
            );

            // Vérifier que l'idempotence a été marquée
            verify(idempotencyService).markCompleted(
                    eq(IDEMPOTENCY_KEY),
                    eq("TRANSFER"),
                    eq(SOURCE_ID.toString()),
                    anyString()
            );
        }

        @Test
        @DisplayName("doit retourner ALREADY_PROCESSED si la clé d'idempotence est déjà traitée")
        void shouldReturnAlreadyProcessedForDuplicateKey() {
            // Arrange
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(true);

            // Act
            TransferResponse response = transferService.executeTransfer(validRequest);

            // Assert
            assertThat(response.getStatus()).isEqualTo("ALREADY_PROCESSED");
            assertThat(response.isIdempotentReplay()).isTrue();
            assertThat(response.getReference()).isEqualTo(IDEMPOTENCY_KEY);

            // Aucune interaction avec les repositories ou événements
            verifyNoInteractions(accountRepository);
            verify(eventPublisher, never()).publish(any(), any(), any(), any(), any());
            verify(eventPublisher, never()).publishWithIdempotency(any(), any(), any(), any(), any(), any());
            verify(idempotencyService, never()).markCompleted(any(), any(), any(), any());
        }
    }

    // ============================================================
    // executeTransfer() — validations
    // ============================================================

    @Nested
    @DisplayName("executeTransfer() — erreurs de validation")
    class ValidationErrorTests {

        @Test
        @DisplayName("doit lever IllegalArgumentException si le compte source n'existe pas")
        void shouldThrowWhenSourceAccountNotFound() {
            // Arrange
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Source account not found");
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le compte cible n'existe pas")
        void shouldThrowWhenTargetAccountNotFound() {
            // Arrange
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target account not found");
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte source n'est pas ACTIF")
        void shouldThrowWhenSourceAccountNotActive() {
            // Arrange
            sourceAccount.setStatus(AccountStatus.FROZEN);
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Source account is not active");
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte cible n'est pas ACTIF")
        void shouldThrowWhenTargetAccountNotActive() {
            // Arrange
            targetAccount.setStatus(AccountStatus.CLOSED);
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Target account is not active");
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si les devises ne correspondent pas (source)")
        void shouldThrowWhenSourceCurrencyMismatch() {
            // Arrange
            sourceAccount.setCurrency("USD");
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Source account currency mismatch");
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si les devises ne correspondent pas (cible)")
        void shouldThrowWhenTargetCurrencyMismatch() {
            // Arrange
            targetAccount.setCurrency("GBP");
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target account currency mismatch");
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si fonds insuffisants (solde + découvert)")
        void shouldThrowWhenInsufficientFunds() {
            // Arrange: balance=1000, overdraft=0, amount=1500 → insuffisant
            TransferRequest largeTransfer = TransferRequest.builder()
                    .sourceAccountNumber(SOURCE_ACCOUNT)
                    .targetAccountNumber(TARGET_ACCOUNT)
                    .amount(BigDecimal.valueOf(1500))
                    .currency(CURRENCY)
                    .description("Montant trop élevé")
                    .idempotencyKey("idemp-002")
                    .build();

            when(idempotencyService.isProcessed("idemp-002")).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));

            // Act & Assert
            assertThatThrownBy(() -> transferService.executeTransfer(largeTransfer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("ne valide pas l'égalité source/cible (aucun check métier — le service le traite)")
        void shouldNotPreventSameAccountTransfer() {
            // Le service ne vérifie pas que source != target.
            // Avec le même numéro, le pessimistic lock trouverait le même compte deux fois.
            // Ce test documente le comportement actuel.
            // Arrange
            TransferRequest sameAccount = TransferRequest.builder()
                    .sourceAccountNumber(SOURCE_ACCOUNT)
                    .targetAccountNumber(SOURCE_ACCOUNT) // même compte
                    .amount(BigDecimal.ONE)
                    .currency(CURRENCY)
                    .description("Même compte")
                    .idempotencyKey("idemp-003")
                    .build();

            when(idempotencyService.isProcessed("idemp-003")).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            // Les deux appels utilisent SOURCE_ACCOUNT (même compte), le premier stub suffit
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            TransferResponse response = transferService.executeTransfer(sameAccount);

            // Assert
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            // Le solde reste inchangé (débit + crédit = 0 net)
            assertThat(response.getSourceBalanceAfter())
                    .isEqualByComparingTo(response.getTargetBalanceAfter());
        }

        @Test
        @DisplayName("ne valide pas le montant ≤ 0 (validation @Positive au niveau du DTO/contrôleur)")
        void shouldNotValidateNonPositiveAmount() {
            // La validation du montant > 0 est assurée par @Positive sur TransferRequest.amount
            // au niveau du contrôleur. Le service ne valide pas cela directement.
            // Arrange
            TransferRequest zeroAmount = TransferRequest.builder()
                    .sourceAccountNumber(SOURCE_ACCOUNT)
                    .targetAccountNumber(TARGET_ACCOUNT)
                    .amount(BigDecimal.ZERO) // montant nul
                    .currency(CURRENCY)
                    .description("Montant zéro")
                    .idempotencyKey("idemp-004")
                    .build();

            when(idempotencyService.isProcessed("idemp-004")).thenReturn(false);
            when(accountRepository.findByAccountNumberWithPessimisticLock(SOURCE_ACCOUNT))
                    .thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByAccountNumberWithPessimisticLock(TARGET_ACCOUNT))
                    .thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act — un montant à 0 passe la validation "insufficient funds"
            // car 0 <= 1000 (availableBalance)
            TransferResponse response = transferService.executeTransfer(zeroAmount);

            // Assert
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            // Les soldes restent inchangés
            assertThat(response.getSourceBalanceAfter()).isEqualByComparingTo(SOURCE_BALANCE);
            assertThat(response.getTargetBalanceAfter()).isEqualByComparingTo(TARGET_BALANCE);
        }
    }

    // ============================================================
    // executeTransfer() — idempotence
    // ============================================================

    @Nested
    @DisplayName("executeTransfer() — idempotence")
    class IdempotencyTests {

        @Test
        @DisplayName("doit retourner ALREADY_PROCESSED sans modifier les soldes si clé déjà traitée")
        void shouldNotModifyBalancesOnDuplicateKey() {
            // Arrange
            when(idempotencyService.isProcessed(IDEMPOTENCY_KEY)).thenReturn(true);

            // Act
            TransferResponse response = transferService.executeTransfer(validRequest);

            // Assert
            assertThat(response.getStatus()).isEqualTo("ALREADY_PROCESSED");
            assertThat(response.isIdempotentReplay()).isTrue();
            assertThat(response.getSourceBalanceAfter()).isNull();
            assertThat(response.getTargetBalanceAfter()).isNull();

            verifyNoInteractions(accountRepository);
            verifyNoInteractions(eventPublisher);
        }
    }
}
