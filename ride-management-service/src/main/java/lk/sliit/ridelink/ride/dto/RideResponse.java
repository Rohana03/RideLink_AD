package lk.sliit.ridelink.ride.dto;

import java.time.Instant;

import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.entity.VehicleType;

public record RideResponse(
        String id,
        String passengerId,
        String driverId,
        String driverUserId,
        String driverName,
        VehicleType vehicleType,
        LocationDto pickupLocation,
        LocationDto destinationLocation,
        RideStatus status,
        Double estimatedDistanceKm,
        Double estimatedFare,
        Double finalFare,
        String currency,
        Boolean fareRecorded,
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
                ride.getDriverUserId(),
                ride.getDriverName(),
                ride.getVehicleType(),
                LocationDto.fromEntity(ride.getPickupLocation()),
                LocationDto.fromEntity(ride.getDestinationLocation()),
                ride.getStatus(),
                ride.getEstimatedDistanceKm(),
                ride.getEstimatedFare(),
                ride.getFinalFare(),
                ride.getCurrency(),
                ride.getFareRecorded(),
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
