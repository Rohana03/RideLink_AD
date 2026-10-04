package lk.sliit.ridelink.ride.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Optional simulated trip measurements. The final fare is calculated by the Fare &amp; Payment
 * Service from these (or from the straight-line distance when they are omitted).
 */
public record CompleteRideRequest(
        @PositiveOrZero(message = "actualDistanceKm must not be negative") Double actualDistanceKm,
        @PositiveOrZero(message = "actualDurationMinutes must not be negative") Double actualDurationMinutes
) {
    public static CompleteRideRequest empty() {
        return new CompleteRideRequest(null, null);
    }
}
