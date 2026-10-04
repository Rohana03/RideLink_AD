package lk.sliit.ridelink.ride.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lk.sliit.ridelink.ride.entity.VehicleType;

/**
 * The passenger is taken from the JWT and the fare estimate from the Fare &amp; Payment Service,
 * so neither can be supplied (or faked) by the client.
 */
public record CreateRideRequest(
        @NotNull(message = "pickupLocation is required") @Valid LocationDto pickupLocation,
        @NotNull(message = "destinationLocation is required") @Valid LocationDto destinationLocation,
        /* Optional: restricts matching to this vehicle type and sets the fare multiplier. */
        VehicleType vehicleType
) {
}
