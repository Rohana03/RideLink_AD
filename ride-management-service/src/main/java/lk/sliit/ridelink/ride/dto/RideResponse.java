package lk.sliit.ridelink.ride.dto;

import java.time.Instant;

import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;

public record RideResponse(
        String id,
        String passengerId,
        String driverId,
        LocationDto pickupLocation,
        LocationDto destinationLocation,
        RideStatus status,
        Double estimatedFare,
        Double finalFare,
        String cancellationReason,
        Instant requestedAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt
) {
    public static RideResponse fromEntity(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                LocationDto.fromEntity(ride.getPickupLocation()),
                LocationDto.fromEntity(ride.getDestinationLocation()),
                ride.getStatus(),
                ride.getEstimatedFare(),
                ride.getFinalFare(),
                ride.getCancellationReason(),
                ride.getRequestedAt(),
                ride.getAssignedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt()
        );
    }
}
