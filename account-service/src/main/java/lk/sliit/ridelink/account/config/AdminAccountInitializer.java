package lk.sliit.ridelink.account.config;

import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * ADMIN accounts cannot self-register, so the first one is created here at start-up
 * from ADMIN_EMAIL / ADMIN_PASSWORD. Nothing happens when either is blank, and an
 * existing account with that email is never modified.
 */
@Component
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ridelink.admin.email:}") String adminEmail,
            @Value("${ridelink.admin.password:}") String adminPassword
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping bootstrap admin creation");
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(email)) {
            log.info("Bootstrap admin {} already exists", email);
            return;
        }

        accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("RideLink Administrator")
                .phoneNumber("+94000000000")
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
        log.info("Created bootstrap admin account {}", email);
    }
}
