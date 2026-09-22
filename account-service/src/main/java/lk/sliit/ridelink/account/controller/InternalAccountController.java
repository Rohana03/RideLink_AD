package lk.sliit.ridelink.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.sliit.ridelink.account.dto.AccountResponse;
import lk.sliit.ridelink.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/internal")
@RequiredArgsConstructor
@Tag(name = "Internal (service-to-service)", description = "Called by other RideLink services with X-Internal-Api-Key")
@SecurityRequirement(name = "internalApiKey")
public class InternalAccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    @Operation(summary = "Look up an account by ID",
            description = "Lets another service confirm a userId exists and is ACTIVE, e.g. before creating a ride or a driver profile")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }
}
