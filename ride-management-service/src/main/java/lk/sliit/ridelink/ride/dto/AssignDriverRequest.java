package lk.sliit.ridelink.ride.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignDriverRequest(
        @NotBlank(message = "driverId is required") String driverId
) {
}
