package lk.sliit.ridelink.account.service;

import lk.sliit.ridelink.account.config.JwtUtil;
import lk.sliit.ridelink.account.dto.*;
import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.exception.*;
import lk.sliit.ridelink.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final String PASSWORD = "Passw0rd123";

    @Mock
    private AccountRepository accountRepository;

    // Real collaborators: hashing and token issuing are part of the behaviour under test.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtUtil jwtUtil =
            new JwtUtil("test-only-jwt-secret-key-not-used-outside-unit-tests-0123456789", 3_600_000);

    private AccountServiceImpl accountService;
    private Account passenger;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, passwordEncoder, jwtUtil);
        passenger = Account.builder()
                .id("acc-psg-001")
                .email("amaya@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Amaya Silva")
                .phoneNumber("+94771112233")
                .role(Role.PASSENGER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private void saveReturnsArgument() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        private RegisterRequest request(Role role) {
            return RegisterRequest.builder()
                    .fullName("  Nimal Perera ")
                    .email("  Nimal.Perera@Example.COM ")
                    .password(PASSWORD)
                    .phoneNumber("+94771234567")
                    .role(role)
                    .build();
        }

        @Test
        @DisplayName("Creates an ACTIVE account with a normalised email and a BCrypt hash")
        void registersDriver() {
            when(accountRepository.existsByEmail("nimal.perera@example.com")).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setId("acc-new");
                return a;
            });

            AccountResponse response = accountService.register(request(Role.DRIVER));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            Account account = saved.getValue();

            assertEquals("nimal.perera@example.com", account.getEmail());
            assertEquals("Nimal Perera", account.getFullName());
            assertEquals(Role.DRIVER, account.getRole());
            assertEquals(AccountStatus.ACTIVE, account.getStatus());
            assertNotEquals(PASSWORD, account.getPasswordHash());
            assertTrue(passwordEncoder.matches(PASSWORD, account.getPasswordHash()));
            assertEquals("acc-new", response.getId());
        }

        @Test
        @DisplayName("Rejects an email that is already registered, ignoring case")
        void rejectsDuplicateEmail() {
            when(accountRepository.existsByEmail("nimal.perera@example.com")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () -> accountService.register(request(Role.PASSENGER)));
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects self-registration as ADMIN")
        void rejectsAdminSelfRegistration() {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> accountService.register(request(Role.ADMIN)));

            assertTrue(ex.getMessage().contains("PASSENGER or DRIVER"));
            verifyNoInteractions(accountRepository);
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Valid credentials return a Bearer token for the account and record the login time")
        void loginSucceeds() {
            when(accountRepository.findByEmail("amaya@example.com")).thenReturn(Optional.of(passenger));
            saveReturnsArgument();

            AuthResponse response = accountService.login(new LoginRequest(" AMAYA@example.com", PASSWORD));

            assertEquals("Bearer", response.getTokenType());
            assertEquals(3600, response.getExpiresIn());
            assertEquals("acc-psg-001", jwtUtil.extractUserId(response.getAccessToken()));
            assertEquals(List.of("PASSENGER"), jwtUtil.extractRoles(response.getAccessToken()));
            assertNotNull(response.getAccount().getLastLoginAt());
        }

        @Test
        @DisplayName("A wrong password gives the generic 'Invalid email or password' error")
        void wrongPassword() {
            when(accountRepository.findByEmail("amaya@example.com")).thenReturn(Optional.of(passenger));

            InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                    () -> accountService.login(new LoginRequest("amaya@example.com", "WrongPass1")));

            assertEquals(AccountServiceImpl.INVALID_CREDENTIALS, ex.getMessage());
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("An unknown email gives the same generic error as a wrong password")
        void unknownEmail() {
            when(accountRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                    () -> accountService.login(new LoginRequest("ghost@example.com", PASSWORD)));

            assertEquals(AccountServiceImpl.INVALID_CREDENTIALS, ex.getMessage());
        }

        @Test
        @DisplayName("A suspended account cannot log in, even with the right password")
        void suspendedAccountBlocked() {
            passenger.setStatus(AccountStatus.SUSPENDED);
            when(accountRepository.findByEmail("amaya@example.com")).thenReturn(Optional.of(passenger));

            AccountNotActiveException ex = assertThrows(AccountNotActiveException.class,
                    () -> accountService.login(new LoginRequest("amaya@example.com", PASSWORD)));

            assertTrue(ex.getMessage().contains("SUSPENDED"));
        }

        @Test
        @DisplayName("A suspended account with a wrong password still gets 401, so status is not leaked")
        void suspendedAccountWrongPasswordLeaksNothing() {
            passenger.setStatus(AccountStatus.SUSPENDED);
            when(accountRepository.findByEmail("amaya@example.com")).thenReturn(Optional.of(passenger));

            assertThrows(InvalidCredentialsException.class,
                    () -> accountService.login(new LoginRequest("amaya@example.com", "WrongPass1")));
        }
    }

    @Nested
    @DisplayName("Own profile and password")
    class Profile {

        @Test
        @DisplayName("Returns the caller's own account")
        void getMyAccount() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));

            assertEquals("amaya@example.com", accountService.getMyAccount("acc-psg-001").getEmail());
        }

        @Test
        @DisplayName("Unknown account ID gives 404")
        void getMissingAccount() {
            when(accountRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> accountService.getMyAccount("missing"));
        }

        @Test
        @DisplayName("Updates name and phone, trimming whitespace")
        void updateProfile() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));
            saveReturnsArgument();

            AccountResponse response = accountService.updateMyProfile("acc-psg-001",
                    new ProfileUpdateRequest(" Amaya S. Silva ", "0771112244"));

            assertEquals("Amaya S. Silva", response.getFullName());
            assertEquals("0771112244", response.getPhoneNumber());
        }

        @Test
        @DisplayName("A deactivated account cannot update its profile")
        void deactivatedCannotUpdateProfile() {
            passenger.setStatus(AccountStatus.DEACTIVATED);
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));

            assertThrows(AccountNotActiveException.class, () -> accountService.updateMyProfile("acc-psg-001",
                    new ProfileUpdateRequest("Amaya", "0771112244")));
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Changes the password when the current one is correct")
        void changePassword() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));
            saveReturnsArgument();

            accountService.changePassword("acc-psg-001", new PasswordChangeRequest(PASSWORD, "NewPassw0rd"));

            assertTrue(passwordEncoder.matches("NewPassw0rd", passenger.getPasswordHash()));
            assertFalse(passwordEncoder.matches(PASSWORD, passenger.getPasswordHash()));
        }

        @Test
        @DisplayName("Rejects a password change when the current password is wrong")
        void changePasswordWrongCurrent() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    accountService.changePassword("acc-psg-001", new PasswordChangeRequest("WrongPass1", "NewPassw0rd")));

            assertEquals("Current password is incorrect", ex.getMessage());
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects a new password equal to the current one")
        void changePasswordSameAsCurrent() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));

            assertThrows(BadRequestException.class, () ->
                    accountService.changePassword("acc-psg-001", new PasswordChangeRequest(PASSWORD, PASSWORD)));
        }
    }

    @Nested
    @DisplayName("Administration")
    class Administration {

        @Test
        @DisplayName("Lists accounts using the right query for each filter combination")
        void listAccountsFilters() {
            when(accountRepository.findAll()).thenReturn(List.of(passenger));
            when(accountRepository.findByRole(Role.PASSENGER)).thenReturn(List.of(passenger));
            when(accountRepository.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of(passenger));
            when(accountRepository.findByRoleAndStatus(Role.DRIVER, AccountStatus.ACTIVE)).thenReturn(List.of());

            assertEquals(1, accountService.listAccounts(null, null).size());
            assertEquals(1, accountService.listAccounts(Role.PASSENGER, null).size());
            assertEquals(1, accountService.listAccounts(null, AccountStatus.ACTIVE).size());
            assertTrue(accountService.listAccounts(Role.DRIVER, AccountStatus.ACTIVE).isEmpty());
        }

        @Test
        @DisplayName("Suspends another account")
        void suspendAccount() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));
            saveReturnsArgument();

            AccountResponse response = accountService.updateStatus("acc-admin", "acc-psg-001", AccountStatus.SUSPENDED);

            assertEquals(AccountStatus.SUSPENDED, response.getStatus());
        }

        @Test
        @DisplayName("Setting the status an account already has does not write to the database")
        void sameStatusIsNoOp() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));

            accountService.updateStatus("acc-admin", "acc-psg-001", AccountStatus.ACTIVE);

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("An admin cannot change their own status")
        void adminCannotSuspendSelf() {
            assertThrows(BadRequestException.class,
                    () -> accountService.updateStatus("acc-admin", "acc-admin", AccountStatus.SUSPENDED));
            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("Changes another account's role")
        void changeRole() {
            when(accountRepository.findById("acc-psg-001")).thenReturn(Optional.of(passenger));
            saveReturnsArgument();

            AccountResponse response = accountService.updateRole("acc-admin", "acc-psg-001", Role.DRIVER);

            assertEquals(Role.DRIVER, response.getRole());
        }

        @Test
        @DisplayName("An admin cannot change their own role")
        void adminCannotDemoteSelf() {
            assertThrows(BadRequestException.class,
                    () -> accountService.updateRole("acc-admin", "acc-admin", Role.PASSENGER));
        }

        @Test
        @DisplayName("Changing the role of an unknown account gives 404")
        void changeRoleMissingAccount() {
            when(accountRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> accountService.updateRole("acc-admin", "missing", Role.DRIVER));
        }
    }
}
