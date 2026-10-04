package lk.sliit.ridelink.fare.dto;

import jakarta.validation.constraints.*;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Contract for "a ride has completed", published by the Ride Management Service to
 * ride.events.exchange with routing key ride.completed (or POSTed to the internal
 * REST endpoint). JSON field names must match exactly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideCompletedEvent {

    @NotBlank(message = "rideId is required")
    private String rideId;

    @NotBlank(message = "passengerId is required")
    private String passengerId;

    private String driverId;

    private String driverUserId;

    /** Optional; CAR is used when omitted. */
    private VehicleType vehicleType;

    @NotNull(message = "pickupLatitude is required")
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double pickupLatitude;

    @NotNull(message = "pickupLongitude is required")
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double pickupLongitude;

    @NotNull(message = "dropoffLatitude is required")
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double dropoffLatitude;

    @NotNull(message = "dropoffLongitude is required")
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double dropoffLongitude;

    /** Optional measured trip distance; when omitted the straight-line distance is used. */
    @PositiveOrZero(message = "actualDistanceKm must not be negative")
    private Double actualDistanceKm;

    /** Optional measured trip time; when omitted it is estimated from the distance. */
    @PositiveOrZero(message = "actualDurationMinutes must not be negative")
    private Double actualDurationMinutes;

    private LocalDateTime completedAt;
}
