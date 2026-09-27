package lk.sliit.ridelink.ride.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRideRequest(
        @NotBlank(message = "passengerId is required") String passengerId,
        @NotNull(message = "pickupLocation is required") @Valid LocationDto pickupLocation,
        @NotNull(message = "destinationLocation is required") @Valid LocationDto destinationLocation,
        @Positive(message = "estimatedFare must be positive") Double estimatedFare
) {
}
