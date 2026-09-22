package lk.sliit.ridelink.account.service;

import lk.sliit.ridelink.account.config.JwtUtil;
import lk.sliit.ridelink.account.dto.*;
import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.exception.*;
import lk.sliit.ridelink.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AccountResponse register(RegisterRequest request) {
        if (request.getRole() == Role.ADMIN) {
            throw new BadRequestException("Only PASSENGER or DRIVER accounts can be self-registered");
        }

        String email = normaliseEmail(request.getEmail());
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered: " + email);
        }

        Account account = Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .role(request.getRole())
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Registered {} account {}", saved.getRole(), saved.getId());
        return AccountResponse.fromEntity(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(normaliseEmail(request.getEmail()))
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        // Checked after the password so an attacker cannot learn an account's status without knowing it.
        ensureActive(account);

        account.setLastLoginAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);

        return AuthResponse.builder()
                .accessToken(jwtUtil.generateToken(saved))
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationSeconds())
                .account(AccountResponse.fromEntity(saved))
                .build();
    }

    @Override
    public AccountResponse getMyAccount(String accountId) {
        return AccountResponse.fromEntity(findAccount(accountId));
    }

    @Override
    public AccountResponse updateMyProfile(String accountId, ProfileUpdateRequest request) {
        Account account = findAccount(accountId);
        ensureActive(account);

        account.setFullName(request.getFullName().trim());
        account.setPhoneNumber(request.getPhoneNumber().trim());
        account.setUpdatedAt(LocalDateTime.now());

        return AccountResponse.fromEntity(accountRepository.save(account));
    }

    @Override
    public void changePassword(String accountId, PasswordChangeRequest request) {
        Account account = findAccount(accountId);
        ensureActive(account);

        // 400, not 401: the caller's token is valid, only the form value is wrong.
        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
        log.info("Password changed for account {}", accountId);
    }

    @Override
    public List<AccountResponse> listAccounts(Role role, AccountStatus status) {
        List<Account> accounts;
        if (role != null && status != null) {
            accounts = accountRepository.findByRoleAndStatus(role, status);
        } else if (role != null) {
            accounts = accountRepository.findByRole(role);
        } else if (status != null) {
            accounts = accountRepository.findByStatus(status);
        } else {
            accounts = accountRepository.findAll();
        }
        return accounts.stream().map(AccountResponse::fromEntity).toList();
    }

    @Override
    public AccountResponse getAccountById(String id) {
        return AccountResponse.fromEntity(findAccount(id));
    }

    @Override
    public AccountResponse updateStatus(String adminId, String targetId, AccountStatus status) {
        rejectSelfChange(adminId, targetId, "status");
        Account account = findAccount(targetId);

        if (account.getStatus() != status) {
            log.info("Account {} status {} -> {} by admin {}", targetId, account.getStatus(), status, adminId);
            account.setStatus(status);
            account.setUpdatedAt(LocalDateTime.now());
            account = accountRepository.save(account);
        }
        return AccountResponse.fromEntity(account);
    }

    @Override
    public AccountResponse updateRole(String adminId, String targetId, Role role) {
        rejectSelfChange(adminId, targetId, "role");
        Account account = findAccount(targetId);

        if (account.getRole() != role) {
            log.info("Account {} role {} -> {} by admin {}", targetId, account.getRole(), role, adminId);
            account.setRole(role);
            account.setUpdatedAt(LocalDateTime.now());
            account = accountRepository.save(account);
        }
        return AccountResponse.fromEntity(account);
    }

    private Account findAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
    }

    private void ensureActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is " + account.getStatus());
        }
    }

    /** Stops an admin from suspending or demoting themselves and locking everyone out. */
    private void rejectSelfChange(String adminId, String targetId, String field) {
        if (adminId != null && adminId.equals(targetId)) {
            throw new BadRequestException("Administrators cannot change their own " + field);
        }
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
