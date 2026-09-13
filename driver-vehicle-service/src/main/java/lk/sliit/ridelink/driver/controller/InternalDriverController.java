package lk.sliit.ridelink.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.driver.dto.DriverResponse;
import lk.sliit.ridelink.driver.dto.EligibleDriverResponse;
import lk.sliit.ridelink.driver.dto.InternalStatusUpdateRequest;
import lk.sliit.ridelink.driver.dto.LocationUpdateRequest;
import lk.sliit.ridelink.driver.entity.VehicleType;
import lk.sliit.ridelink.driver.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers/internal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Driver & Vehicle API", description = "Interservice endpoints protected by X-Internal-Api-Key (called by Ride Management Service)")
@SecurityRequirement(name = "internalApiKey")
public class InternalDriverController {

    private final DriverService driverService;

    @GetMapping("/eligible")
    @Operation(summary = "Get eligible available drivers", description = "Retrieves available active drivers sorted by proximity to pickup coordinates using the Haversine formula")
    public ResponseEntity<List<EligibleDriverResponse>> getEligibleDrivers(
            @RequestParam Double pickupLatitude,
            @RequestParam Double pickupLongitude,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false, defaultValue = "5.0") Double radiusKm,
            @RequestParam(required = false, defaultValue = "5") Integer limit
    ) {
        log.info("Interservice request: get eligible drivers near ({}, {}) vehicleType: {}",
                pickupLatitude, pickupLongitude, vehicleType);

        List<EligibleDriverResponse> eligibleDrivers = driverService.findEligibleDrivers(
                pickupLatitude,
                pickupLongitude,
                vehicleType,
                radiusKm,
                limit
        );
        return ResponseEntity.ok(eligibleDrivers);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by ID (internal)", description = "Interservice retrieval of driver and vehicle profile")
    public ResponseEntity<DriverResponse> getDriverById(@PathVariable Long id) {
        DriverResponse response = driverService.getDriverById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update driver status (internal)", description = "Used by Ride Management Service to set ON_TRIP or AVAILABLE")
    public ResponseEntity<DriverResponse> updateDriverStatus(
            @PathVariable Long id,
            @Valid @RequestBody InternalStatusUpdateRequest request
    ) {
        log.info("Interservice status update for driver ID {}: {}", id, request.getStatus());
        DriverResponse response = driverService.updateInternalStatus(id, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/location")
    @Operation(summary = "Update driver location (internal/simulation)", description = "Direct simulated location update for automated test scripts")
    public ResponseEntity<DriverResponse> updateDriverLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        DriverResponse response = driverService.updateInternalLocation(id, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(response);
    }
}
