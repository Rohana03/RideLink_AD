package lk.sliit.ridelink.fare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.fare.dto.FareBreakdown;
import lk.sliit.ridelink.fare.dto.FareEstimateRequest;
import lk.sliit.ridelink.fare.service.FareCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
@Tag(name = "Fare Estimation", description = "Fare estimate for a pickup and destination using the documented fare rule")
@SecurityRequirement(name = "bearerAuth")
public class FareController {

    private final FareCalculator fareCalculator;

    @PostMapping("/estimate")
    @Operation(summary = "Estimate a fare",
            description = "fare = max(minimumFare, (baseFare + perKmRate x km + perMinRate x minutes) x vehicleMultiplier). " +
                    "km is the straight-line (Haversine) distance; minutes assume the configured average speed.")
    public ResponseEntity<FareBreakdown> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareCalculator.estimate(request));
    }
}
