package com.corebanking.account.service;

import com.corebanking.account.entity.Account;
import com.corebanking.account.repository.AccountRepository;
import com.corebanking.common.enums.AccountStatus;
import com.corebanking.common.event.EventPublisher;
import com.corebanking.common.event.EventType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCommandService — Tests unitaires")
class AccountCommandServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AccountCommandService service;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    // --- Données communes ---
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ACCOUNT_NUMBER = "FR7630004000031234567890145";
    private static final String OWNER_NAME = "Jean Dupont";
    private static final String OWNER_EMAIL = "jean.dupont@example.com";
    private static final String CURRENCY = "EUR";
    private static final BigDecimal OVERDRAFT_LIMIT = BigDecimal.valueOf(500);
    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(1000);

    private Account createActiveAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .accountNumber(ACCOUNT_NUMBER)
                .ownerName(OWNER_NAME)
                .ownerEmail(OWNER_EMAIL)
                .currency(CURRENCY)
                .balance(INITIAL_BALANCE)
                .overdraftLimit(OVERDRAFT_LIMIT)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ============================================================
    // createAccount()
    // ============================================================

    @Nested
    @DisplayName("createAccount()")
    class CreateAccountTests {

        @Test
        @DisplayName("doit créer un compte avec succès et publier un événement ACCOUNT_CREATED")
        void shouldCreateAccountSuccessfully() {
            // Arrange
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());
            Account savedAccount = Account.builder()
                    .id(ACCOUNT_ID)
                    .accountNumber(ACCOUNT_NUMBER)
                    .ownerName(OWNER_NAME)
                    .ownerEmail(OWNER_EMAIL)
                    .currency(CURRENCY)
                    .balance(BigDecimal.ZERO)
                    .overdraftLimit(OVERDRAFT_LIMIT)
                    .status(AccountStatus.ACTIVE)
                    .version(0L)
                    .build();
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            // Act
            Account result = service.createAccount(ACCOUNT_NUMBER, OWNER_NAME, OWNER_EMAIL, CURRENCY, OVERDRAFT_LIMIT);

            // Assert
            assertThat(result.getId()).isEqualTo(ACCOUNT_ID);
            assertThat(result.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(result.getOwnerName()).isEqualTo(OWNER_NAME);
            assertThat(result.getOwnerEmail()).isEqualTo(OWNER_EMAIL);
            assertThat(result.getCurrency()).isEqualTo(CURRENCY);
            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getOverdraftLimit()).isEqualByComparingTo(OVERDRAFT_LIMIT);
            assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);

            verify(accountRepository).findByAccountNumber(ACCOUNT_NUMBER);
            verify(accountRepository).save(accountCaptor.capture());
            Account captured = accountCaptor.getValue();
            assertThat(captured.getOwnerName()).isEqualTo(OWNER_NAME);

            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.ACCOUNT_CREATED.name()),
                    any(Map.class),
                    any(Map.class)
            );
        }

        @Test
        @DisplayName("doit utiliser 'XOF' par défaut si la devise est null")
        void shouldDefaultToXofCurrencyWhenNull() {
            // Arrange
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> {
                Account a = i.getArgument(0);
                if (a.getId() == null) {
                    a.setId(UUID.randomUUID());
                }
                return a;
            });

            // Act
            Account result = service.createAccount(ACCOUNT_NUMBER, OWNER_NAME, OWNER_EMAIL, null, null);

            // Assert
            assertThat(result.getCurrency()).isEqualTo("XOF");
            assertThat(result.getOverdraftLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le numéro de compte existe déjà")
        void shouldThrowWhenAccountNumberAlreadyExists() {
            // Arrange
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(Optional.of(createActiveAccount()));

            // Act & Assert
            assertThatThrownBy(() ->
                    service.createAccount(ACCOUNT_NUMBER, OWNER_NAME, OWNER_EMAIL, CURRENCY, OVERDRAFT_LIMIT)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account number already exists");

            verify(accountRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("ne valide pas le format de l'email (validation @Email au niveau contrôleur)")
        void shouldNotValidateEmailFormat() {
            // La validation @Email est gérée par Jakarta Validation au niveau
            // du DTO/contrôleur. Le service accepte tout format d'email.
            // Arrange
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> {
                Account a = i.getArgument(0);
                if (a.getId() == null) {
                    a.setId(UUID.randomUUID());
                }
                return a;
            });

            // Act
            Account result = service.createAccount(ACCOUNT_NUMBER, OWNER_NAME, "email-sans-arobase", CURRENCY, OVERDRAFT_LIMIT);

            // Assert
            assertThat(result.getOwnerEmail()).isEqualTo("email-sans-arobase");
            assertThat(result.getId()).isNotNull();
        }
    }

    // ============================================================
    // deposit()
    // ============================================================

    @Nested
    @DisplayName("deposit()")
    class DepositTests {

        @Test
        @DisplayName("doit créditer le solde et publier un événement DEPOSIT_EXECUTED")
        void shouldIncreaseBalance() {
            // Arrange
            Account account = createActiveAccount();
            BigDecimal depositAmount = BigDecimal.valueOf(200);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.deposit(ACCOUNT_ID, depositAmount, "DEP-001", "Dépôt test");

            // Assert
            assertThat(result.getBalance())
                    .isEqualByComparingTo(INITIAL_BALANCE.add(depositAmount)); // 1000 + 200 = 1200

            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.DEPOSIT_EXECUTED.name()),
                    any(Map.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le montant est négatif")
        void shouldThrowWhenAmountIsNegative() {
            // Act & Assert
            assertThatThrownBy(() ->
                    service.deposit(ACCOUNT_ID, BigDecimal.valueOf(-50), "DEP-002", "Montant négatif")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Deposit amount must be positive");

            verifyNoInteractions(accountRepository);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le montant est zéro")
        void shouldThrowWhenAmountIsZero() {
            // Act & Assert
            assertThatThrownBy(() ->
                    service.deposit(ACCOUNT_ID, BigDecimal.ZERO, "DEP-003", "Montant zéro")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Deposit amount must be positive");

            verifyNoInteractions(accountRepository);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte n'est pas ACTIF")
        void shouldThrowWhenAccountNotActive() {
            // Arrange
            Account frozenAccount = createActiveAccount();
            frozenAccount.setStatus(AccountStatus.FROZEN);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(frozenAccount));

            // Act & Assert
            assertThatThrownBy(() ->
                    service.deposit(ACCOUNT_ID, BigDecimal.valueOf(100), "DEP-004", "Compte gelé")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }
    }

    // ============================================================
    // withdraw()
    // ============================================================

    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("doit débiter le solde et publier un événement WITHDRAWAL_EXECUTED")
        void shouldDecreaseBalance() {
            // Arrange
            Account account = createActiveAccount();
            BigDecimal withdrawalAmount = BigDecimal.valueOf(300);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.withdraw(ACCOUNT_ID, withdrawalAmount, "WTH-001", "Retrait test");

            // Assert
            assertThat(result.getBalance())
                    .isEqualByComparingTo(INITIAL_BALANCE.subtract(withdrawalAmount)); // 1000 - 300 = 700

            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.WITHDRAWAL_EXECUTED.name()),
                    any(Map.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le montant est négatif")
        void shouldThrowWhenAmountIsNegative() {
            // Act & Assert
            assertThatThrownBy(() ->
                    service.withdraw(ACCOUNT_ID, BigDecimal.valueOf(-10), "WTH-002", "Montant négatif")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Withdrawal amount must be positive");

            verifyNoInteractions(accountRepository);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le montant est zéro")
        void shouldThrowWhenAmountIsZero() {
            // Act & Assert
            assertThatThrownBy(() ->
                    service.withdraw(ACCOUNT_ID, BigDecimal.ZERO, "WTH-003", "Montant zéro")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Withdrawal amount must be positive");

            verifyNoInteractions(accountRepository);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si fonds insuffisants (solde + découvert)")
        void shouldThrowWhenInsufficientFunds() {
            // Arrange
            Account account = createActiveAccount(); // balance=1000, overdraft=500 → available=1500
            BigDecimal tooLarge = BigDecimal.valueOf(2000); // > 1500
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() ->
                    service.withdraw(ACCOUNT_ID, tooLarge, "WTH-004", "Fonds insuffisants")
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("doit autoriser le retrait jusqu'à la limite du découvert")
        void shouldAllowWithdrawalUpToOverdraftLimit() {
            // Arrange: balance=1000, overdraft=500 → available=1500
            Account account = createActiveAccount();
            BigDecimal amountAtLimit = BigDecimal.valueOf(1500); // exactly available
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.withdraw(ACCOUNT_ID, amountAtLimit, "WTH-005", "Limite découvert");

            // Assert
            assertThat(result.getBalance())
                    .isEqualByComparingTo(BigDecimal.valueOf(-500)); // 1000 - 1500 = -500

            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.OVERDRAFT_APPLIED.name()),
                    any(Map.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte n'est pas ACTIF")
        void shouldThrowWhenAccountNotActive() {
            // Arrange
            Account closedAccount = createActiveAccount();
            closedAccount.setStatus(AccountStatus.CLOSED);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(closedAccount));

            // Act & Assert
            assertThatThrownBy(() ->
                    service.withdraw(ACCOUNT_ID, BigDecimal.valueOf(100), "WTH-006", "Compte fermé")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }
    }

    // ============================================================
    // freezeAccount()
    // ============================================================

    @Nested
    @DisplayName("freezeAccount()")
    class FreezeAccountTests {

        @Test
        @DisplayName("doit geler un compte actif et publier un événement ACCOUNT_FROZEN")
        void shouldFreezeActiveAccount() {
            // Arrange
            Account account = createActiveAccount();
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.freezeAccount(ACCOUNT_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.ACCOUNT_FROZEN.name()),
                    any(Map.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte n'est pas ACTIF (ex: déjà FROZEN)")
        void shouldThrowWhenAccountNotActive() {
            // Arrange
            Account frozenAccount = createActiveAccount();
            frozenAccount.setStatus(AccountStatus.FROZEN);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(frozenAccount));

            // Act & Assert
            assertThatThrownBy(() -> service.freezeAccount(ACCOUNT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot freeze account with status");

            verify(accountRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte est CLOSED")
        void shouldThrowWhenAccountIsClosed() {
            // Arrange
            Account closedAccount = createActiveAccount();
            closedAccount.setStatus(AccountStatus.CLOSED);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(closedAccount));

            // Act & Assert
            assertThatThrownBy(() -> service.freezeAccount(ACCOUNT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot freeze account with status");
        }
    }

    // ============================================================
    // closeAccount()
    // ============================================================

    @Nested
    @DisplayName("closeAccount()")
    class CloseAccountTests {

        @Test
        @DisplayName("doit clôturer un compte actif avec solde nul et publier ACCOUNT_CLOSED")
        void shouldCloseActiveAccountWithZeroBalance() {
            // Arrange
            Account account = createActiveAccount();
            account.setBalance(BigDecimal.ZERO); // solde nul requis
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.closeAccount(ACCOUNT_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(AccountStatus.CLOSED);
            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.ACCOUNT_CLOSED.name()),
                    any(Map.class),
                    isNull()
            );
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le solde n'est pas nul")
        void shouldThrowWhenBalanceIsNotZero() {
            // Arrange
            Account account = createActiveAccount(); // balance = 1000
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            // Act & Assert
            assertThatThrownBy(() -> service.closeAccount(ACCOUNT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("non-zero balance");
        }

        @Test
        @DisplayName("doit lever IllegalStateException si le compte est déjà CLOSED")
        void shouldThrowWhenAlreadyClosed() {
            // Arrange
            Account closedAccount = createActiveAccount();
            closedAccount.setBalance(BigDecimal.ZERO);
            closedAccount.setStatus(AccountStatus.CLOSED);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(closedAccount));

            // Act & Assert
            assertThatThrownBy(() -> service.closeAccount(ACCOUNT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already closed");
        }

        @Test
        @DisplayName("doit clôturer un compte FROZEN si le solde est nul")
        void shouldCloseFrozenAccountWithZeroBalance() {
            // Arrange
            Account frozenAccount = createActiveAccount();
            frozenAccount.setBalance(BigDecimal.ZERO);
            frozenAccount.setStatus(AccountStatus.FROZEN);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(frozenAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Account result = service.closeAccount(ACCOUNT_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(AccountStatus.CLOSED);
            verify(eventPublisher).publish(
                    eq("Account"),
                    eq(ACCOUNT_ID.toString()),
                    eq(EventType.ACCOUNT_CLOSED.name()),
                    any(Map.class),
                    isNull()
            );
        }
    }

    // ============================================================
    // getAccount() / getAccountByNumber()
    // ============================================================

    @Nested
    @DisplayName("getAccount()")
    class GetAccountTests {

        @Test
        @DisplayName("doit retourner le compte s'il existe")
        void shouldReturnAccountWhenFound() {
            // Arrange
            Account account = createActiveAccount();
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

            // Act
            Account result = service.getAccount(ACCOUNT_ID);

            // Assert
            assertThat(result.getId()).isEqualTo(ACCOUNT_ID);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le compte n'existe pas")
        void shouldThrowWhenNotFound() {
            // Arrange
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getAccount(ACCOUNT_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("getAccountByNumber()")
    class GetAccountByNumberTests {

        @Test
        @DisplayName("doit retourner le compte si le numéro existe")
        void shouldReturnAccountWhenFound() {
            // Arrange
            Account account = createActiveAccount();
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            // Act
            Account result = service.getAccountByNumber(ACCOUNT_NUMBER);

            // Assert
            assertThat(result.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        }

        @Test
        @DisplayName("doit lever IllegalArgumentException si le numéro n'existe pas")
        void shouldThrowWhenNotFound() {
            // Arrange
            when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getAccountByNumber(ACCOUNT_NUMBER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account not found");
        }
    }
}
