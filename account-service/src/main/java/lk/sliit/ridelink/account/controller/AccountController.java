package lk.sliit.ridelink.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.account.dto.*;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Management", description = "Own profile for any signed-in user; account administration for ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/me")
    @Operation(summary = "Get my account", description = "Returns the profile of the signed-in user")
    public ResponseEntity<AccountResponse> getMyAccount(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(accountService.getMyAccount(userId));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile", description = "Updates full name and phone number. Email, role and status cannot be changed here.")
    public ResponseEntity<AccountResponse> updateMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateMyProfile(userId, request));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change my password", description = "Requires the current password. Returns 204 on success.")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        accountService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List accounts (ADMIN)", description = "Optionally filter by role and/or status")
    public ResponseEntity<List<AccountResponse>> listAccounts(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status
    ) {
        return ResponseEntity.ok(accountService.listAccounts(role, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account by ID (ADMIN)")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable String id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change account status (ADMIN)",
            description = "ACTIVE, SUSPENDED or DEACTIVATED. Non-active accounts cannot log in. Admins cannot change their own status.")
    public ResponseEntity<AccountResponse> updateStatus(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateStatus(adminId, id, request.getStatus()));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change account role (ADMIN)",
            description = "The new role applies from the user's next login. Admins cannot change their own role.")
    public ResponseEntity<AccountResponse> updateRole(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id,
            @Valid @RequestBody RoleUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateRole(adminId, id, request.getRole()));
    }
}
