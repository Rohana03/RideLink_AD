package lk.sliit.ridelink.ride.exception;

/** Mapped to 403 when a signed-in user acts on a ride that is not theirs. */
public class ForbiddenRideAccessException extends RuntimeException {
    public ForbiddenRideAccessException(String message) {
        super(message);
    }
}
