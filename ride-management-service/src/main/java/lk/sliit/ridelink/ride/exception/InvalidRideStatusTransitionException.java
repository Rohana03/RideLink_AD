package lk.sliit.ridelink.ride.exception;

import lk.sliit.ridelink.ride.entity.RideStatus;

public class InvalidRideStatusTransitionException extends RuntimeException {
    public InvalidRideStatusTransitionException(RideStatus current, RideStatus target) {
        super("Cannot transition ride from " + current + " to " + target);
    }
}
