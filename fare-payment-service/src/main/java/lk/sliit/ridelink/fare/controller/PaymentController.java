package lk.sliit.ridelink.fare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.fare.dto.PaymentRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Simulated payment for completed rides and payment status")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{rideId}/pay")
    @Operation(summary = "Pay for a completed ride (PASSENGER)",
            description = "Simulated. CASH and WALLET always succeed; CARD succeeds unless the card number ends in 0000, " +
                    "which returns 402 and marks the payment FAILED so it can be retried. A PAID ride cannot be paid again (409).")
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal String userId,
            @PathVariable String rideId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.pay(rideId, userId, request));
    }

    @GetMapping("/me")
    @Operation(summary = "My payments (PASSENGER)", description = "Newest first")
    public ResponseEntity<List<PaymentResponse>> myPayments(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(paymentService.getPaymentsForPassenger(userId));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "Payment status for a ride", description = "Visible to the ride's passenger, its driver, or an admin")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String rideId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPayment(
                rideId, (String) authentication.getPrincipal(), CallerRoles.isAdmin(authentication)));
    }

    @GetMapping
    @Operation(summary = "List all payments (ADMIN)", description = "Optionally filter by status")
    public ResponseEntity<List<PaymentResponse>> listPayments(@RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(paymentService.listPayments(status));
    }
}
