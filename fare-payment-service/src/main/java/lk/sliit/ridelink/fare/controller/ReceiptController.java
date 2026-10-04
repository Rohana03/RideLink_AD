package lk.sliit.ridelink.fare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.sliit.ridelink.fare.dto.ReceiptResponse;
import lk.sliit.ridelink.fare.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipts", description = "Receipt retrieval for paid rides")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptController {

    private final PaymentService paymentService;

    @GetMapping("/{rideId}")
    @Operation(summary = "Get the receipt for a ride",
            description = "Returns 409 until the ride is PAID. Visible to the ride's passenger, its driver, or an admin.")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable String rideId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getReceipt(
                rideId, (String) authentication.getPrincipal(), CallerRoles.isAdmin(authentication)));
    }
}
