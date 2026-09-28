package lk.sliit.ridelink.ride.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
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

    private String passengerId;
    private String driverId;

    private Location pickupLocation;
    private Location destinationLocation;

    private RideStatus status;

    private Double estimatedFare;
    private Double finalFare;
    private String cancellationReason;

    private Instant requestedAt;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
