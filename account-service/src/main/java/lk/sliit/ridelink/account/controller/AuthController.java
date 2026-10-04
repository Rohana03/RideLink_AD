package lk.sliit.ridelink.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.account.dto.AccountResponse;
import lk.sliit.ridelink.account.dto.AuthResponse;
import lk.sliit.ridelink.account.dto.LoginRequest;
import lk.sliit.ridelink.account.dto.RegisterRequest;
import lk.sliit.ridelink.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Public endpoints for account registration and login (JWT issuance)")
public class AuthController {

    private final AccountService accountService;

    @PostMapping("/register")
    @Operation(summary = "Register a passenger or driver account",
            description = "Creates an ACTIVE account. Role must be PASSENGER or DRIVER; ADMIN cannot self-register.")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration requested for role {}", request.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT",
            description = "Returns a Bearer token accepted by every RideLink service. Suspended or deactivated accounts get 403.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(accountService.login(request));
    }
}
