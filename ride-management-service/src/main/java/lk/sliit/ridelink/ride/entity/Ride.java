package lk.sliit.ridelink.ride.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rides")
public class Ride {

    @Id
    private String id;

    /** Optimistic lock: two concurrent updates (e.g. double assignment) cannot both be saved. */
    @Version
    private Long version;

    /** Account Service userId of the passenger (taken from the JWT). */
    private String passengerId;

    /** Driver &amp; Vehicle Service driver ID of the assigned driver. */
    private String driverId;

    /** Account Service userId of the assigned driver, used to check who may accept/start/complete. */
    private String driverUserId;

    private String driverName;

    private VehicleType vehicleType;

    private Location pickupLocation;
    private Location destinationLocation;

    private RideStatus status;

    private Double estimatedDistanceKm;
    private Double estimatedFare;
    private Double finalFare;
    private String currency;

    /** True once Fare &amp; Payment has recorded the completed ride and opened a payment. */
    private Boolean fareRecorded;

    private String cancellationReason;

    private Instant requestedAt;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
