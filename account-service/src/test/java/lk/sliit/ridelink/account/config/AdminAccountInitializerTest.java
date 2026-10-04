package lk.sliit.ridelink.account.config;

import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private AccountRepository accountRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    @DisplayName("Creates an ACTIVE admin when ADMIN_EMAIL and ADMIN_PASSWORD are set")
    void createsAdmin() {
        when(accountRepository.existsByEmail("admin@ridelink.lk")).thenReturn(false);

        new AdminAccountInitializer(accountRepository, passwordEncoder, " Admin@RideLink.lk ", "AdminPass123")
                .run(null);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertEquals("admin@ridelink.lk", saved.getValue().getEmail());
        assertEquals(Role.ADMIN, saved.getValue().getRole());
        assertEquals(AccountStatus.ACTIVE, saved.getValue().getStatus());
        assertTrue(passwordEncoder.matches("AdminPass123", saved.getValue().getPasswordHash()));
    }

    @Test
    @DisplayName("Does nothing when the admin already exists")
    void skipsExistingAdmin() {
        when(accountRepository.existsByEmail("admin@ridelink.lk")).thenReturn(true);

        new AdminAccountInitializer(accountRepository, passwordEncoder, "admin@ridelink.lk", "AdminPass123")
                .run(null);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Does nothing when the bootstrap values are not configured")
    void skipsWhenNotConfigured() {
        new AdminAccountInitializer(accountRepository, passwordEncoder, "", "").run(null);
        new AdminAccountInitializer(accountRepository, passwordEncoder, "admin@ridelink.lk", " ").run(null);

        verifyNoInteractions(accountRepository);
    }
}
