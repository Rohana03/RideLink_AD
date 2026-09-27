package lk.sliit.ridelink.ride.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteRideRequest(
        @NotNull(message = "finalFare is required") @Positive(message = "finalFare must be positive") Double finalFare
) {
}
