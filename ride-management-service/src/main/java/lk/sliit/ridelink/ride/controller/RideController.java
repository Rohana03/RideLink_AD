package lk.sliit.ridelink.ride.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.sliit.ridelink.ride.dto.AssignDriverRequest;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.service.RideService;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.createRide(request));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.getRide(rideId));
    }

    @GetMapping
    public ResponseEntity<List<RideResponse>> getRides(
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) RideStatus status) {
        return ResponseEntity.ok(rideService.getRides(passengerId, driverId, status));
    }

    @PatchMapping("/{rideId}/assign")
    public ResponseEntity<RideResponse> assignDriver(@PathVariable String rideId,
                                                       @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.ok(rideService.assignDriver(rideId, request));
    }

    @PatchMapping("/{rideId}/accept")
    public ResponseEntity<RideResponse> acceptRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.acceptRide(rideId));
    }

    @PatchMapping("/{rideId}/start")
    public ResponseEntity<RideResponse> startRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    @PatchMapping("/{rideId}/complete")
    public ResponseEntity<RideResponse> completeRide(@PathVariable String rideId,
                                                       @Valid @RequestBody CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.completeRide(rideId, request));
    }

    @PatchMapping("/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancelRide(@PathVariable String rideId,
                                                     @RequestBody(required = false) CancelRideRequest request) {
        return ResponseEntity.ok(rideService.cancelRide(rideId, request != null ? request : new CancelRideRequest(null)));
    }
}
