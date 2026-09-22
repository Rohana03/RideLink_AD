package lk.sliit.ridelink.account.service;

import lk.sliit.ridelink.account.dto.*;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;

import java.util.List;

public interface AccountService {

    AccountResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AccountResponse getMyAccount(String accountId);

    AccountResponse updateMyProfile(String accountId, ProfileUpdateRequest request);

    void changePassword(String accountId, PasswordChangeRequest request);

    /** Both filters are optional; null means "any". */
    List<AccountResponse> listAccounts(Role role, AccountStatus status);

    AccountResponse getAccountById(String id);

    AccountResponse updateStatus(String adminId, String targetId, AccountStatus status);

    AccountResponse updateRole(String adminId, String targetId, Role role);
}
