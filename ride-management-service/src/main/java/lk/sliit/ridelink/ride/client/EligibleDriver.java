package lk.sliit.ridelink.ride.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The fields Ride Management needs from Driver &amp; Vehicle's
 * GET /api/drivers/internal/eligible response; other fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibleDriver(
        String driverId,
        String userId,
        String fullName,
        Double distanceKm,
        Vehicle vehicle
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(String vehicleType, String make, String model, String licensePlate) {
    }
}
