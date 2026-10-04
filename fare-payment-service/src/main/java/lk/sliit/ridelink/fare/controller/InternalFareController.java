package lk.sliit.ridelink.fare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.fare.dto.FareBreakdown;
import lk.sliit.ridelink.fare.dto.FareEstimateRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.service.FareCalculator;
import lk.sliit.ridelink.fare.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Internal (service-to-service)", description = "Called by the Ride Management Service with X-Internal-Api-Key")
@SecurityRequirement(name = "internalApiKey")
public class InternalFareController {

    private final FareCalculator fareCalculator;
    private final PaymentService paymentService;

    @PostMapping("/api/fares/internal/estimate")
    @Operation(summary = "Fare estimate for a new ride request (sync REST)",
            description = "Same rule as POST /api/fares/estimate; used by Ride Management when a ride is requested")
    public ResponseEntity<FareBreakdown> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareCalculator.estimate(request));
    }

    @PostMapping("/api/payments/internal/ride-completed")
    @Operation(summary = "Report a completed ride (REST alternative to the RabbitMQ event)",
            description = "Calculates the final fare and opens a PENDING payment. Idempotent per rideId: " +
                    "repeating the call returns the existing payment unchanged.")
    public ResponseEntity<PaymentResponse> rideCompleted(@Valid @RequestBody RideCompletedEvent event) {
        return ResponseEntity.ok(paymentService.recordCompletedRide(event));
    }
}
