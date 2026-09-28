package lk.sliit.ridelink.ride.messaging;

import java.time.LocalDateTime;

import lk.sliit.ridelink.ride.entity.VehicleType;

/**
 * "A ride has completed" contract shared with the Fare &amp; Payment Service. Sent either as a
 * RabbitMQ message (ride.events.exchange / ride.completed) or as the body of
 * POST /api/payments/internal/ride-completed. Field names must match exactly.
 */
public record RideCompletedEvent(
        String rideId,
        String passengerId,
        String driverId,
        String driverUserId,
        VehicleType vehicleType,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropoffLatitude,
        Double dropoffLongitude,
        Double actualDistanceKm,
        Double actualDurationMinutes,
        LocalDateTime completedAt
) {
}
