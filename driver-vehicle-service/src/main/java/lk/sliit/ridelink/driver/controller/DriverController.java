package lk.sliit.ridelink.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sliit.ridelink.driver.dto.*;
import lk.sliit.ridelink.driver.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver Management", description = "Endpoints for driver profile, vehicle details, availability, and location")
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    @Operation(summary = "Register driver profile and vehicle", description = "Creates a new driver profile with linked vehicle for the authenticated user")
    public ResponseEntity<DriverResponse> registerDriver(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody DriverRegistrationRequest request
    ) {
        log.info("Driver registration requested by user: {}", userId);
        DriverResponse response = driverService.registerDriver(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current driver profile", description = "Fetches the full operational profile and vehicle details of the authenticated driver")
    public ResponseEntity<DriverResponse> getCurrentDriver(@AuthenticationPrincipal String userId) {
        DriverResponse response = driverService.getDriverByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @Operation(summary = "Update driver profile", description = "Updates operational profile details such as name, phone, email, and service area")
    public ResponseEntity<DriverResponse> updateProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody DriverUpdateRequest request
    ) {
        DriverResponse response = driverService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/vehicle")
    @Operation(summary = "Update vehicle details", description = "Updates the vehicle information linked to the authenticated driver")
    public ResponseEntity<DriverResponse> updateVehicle(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody VehicleRequest request
    ) {
        DriverResponse response = driverService.updateVehicle(userId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/availability")
    @Operation(summary = "Update driver availability status", description = "Toggles driver availability status (AVAILABLE or OFFLINE)")
    public ResponseEntity<DriverResponse> updateAvailability(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AvailabilityUpdateRequest request
    ) {
        DriverResponse response = driverService.updateAvailability(userId, request.getAvailabilityStatus());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/location")
    @Operation(summary = "Update simulated current location", description = "Updates the driver's current GPS coordinates (latitude and longitude)")
    public ResponseEntity<DriverResponse> updateLocation(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        DriverResponse response = driverService.updateLocation(userId, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by ID", description = "Retrieves public driver operational profile and vehicle by driver ID")
    public ResponseEntity<DriverResponse> getDriverById(@PathVariable Long id) {
        DriverResponse response = driverService.getDriverById(id);
        return ResponseEntity.ok(response);
    }
}
