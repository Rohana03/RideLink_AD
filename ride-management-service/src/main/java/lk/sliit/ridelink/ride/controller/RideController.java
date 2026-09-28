package lk.sliit.ridelink.ride.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.service.RideCaller;
import lk.sliit.ridelink.ride.service.RideService;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides", description = "Ride request, driver assignment and the ride status lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @Operation(summary = "Request a ride (PASSENGER)",
            description = "The passenger comes from the token. The fare estimate is fetched from the Fare & Payment Service.")
    public ResponseEntity<RideResponse> createRide(Authentication authentication,
                                                   @Valid @RequestBody CreateRideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rideService.createRide(caller(authentication).userId(), request));
    }

    @GetMapping("/me")
    @Operation(summary = "My rides", description = "Rides where the caller is the passenger or the assigned driver, newest first")
    public ResponseEntity<List<RideResponse>> getMyRides(Authentication authentication) {
        return ResponseEntity.ok(rideService.getMyRides(caller(authentication)));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "Get a ride", description = "Visible to the ride's passenger, its assigned driver, or an admin")
    public ResponseEntity<RideResponse> getRide(Authentication authentication, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.getRide(rideId, caller(authentication)));
    }

    @GetMapping
    @Operation(summary = "List all rides (ADMIN)", description = "Optional filters: passengerId, driverId or status")
    public ResponseEntity<List<RideResponse>> getRides(
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) RideStatus status) {
        return ResponseEntity.ok(rideService.getRides(passengerId, driverId, status));
    }

    @PatchMapping("/{rideId}/assign")
    @Operation(summary = "Assign the nearest available driver (PASSENGER of the ride, or ADMIN)",
            description = "Asks the Driver & Vehicle Service for AVAILABLE drivers within the matching radius, nearest first, "
                    + "and reserves the first one (driver becomes ON_TRIP). 409 if no driver is available.")
    public ResponseEntity<RideResponse> assignDriver(Authentication authentication, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.assignDriver(rideId, caller(authentication)));
    }

    @PatchMapping("/{rideId}/accept")
    @Operation(summary = "Accept the assigned ride (assigned DRIVER)")
    public ResponseEntity<RideResponse> acceptRide(Authentication authentication, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.acceptRide(rideId, caller(authentication)));
    }

    @PatchMapping("/{rideId}/start")
    @Operation(summary = "Start the ride (assigned DRIVER)")
    public ResponseEntity<RideResponse> startRide(Authentication authentication, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId, caller(authentication)));
    }

    @PatchMapping("/{rideId}/complete")
    @Operation(summary = "Complete the ride (assigned DRIVER)",
            description = "Releases the driver (AVAILABLE) and hands the ride to Fare & Payment, which calculates the final fare. "
                    + "Optional body: simulated actualDistanceKm / actualDurationMinutes.")
    public ResponseEntity<RideResponse> completeRide(Authentication authentication, @PathVariable String rideId,
                                                     @Valid @RequestBody(required = false) CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.completeRide(rideId, caller(authentication),
                request != null ? request : CompleteRideRequest.empty()));
    }

    @PatchMapping("/{rideId}/cancel")
    @Operation(summary = "Cancel the ride (its passenger, assigned driver, or ADMIN)",
            description = "Allowed before the ride starts; a reserved driver is released back to AVAILABLE.")
    public ResponseEntity<RideResponse> cancelRide(Authentication authentication, @PathVariable String rideId,
                                                   @RequestBody(required = false) CancelRideRequest request) {
        return ResponseEntity.ok(rideService.cancelRide(rideId, caller(authentication),
                request != null ? request : new CancelRideRequest(null)));
    }

    private static RideCaller caller(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new RideCaller((String) authentication.getPrincipal(), admin);
    }
}
