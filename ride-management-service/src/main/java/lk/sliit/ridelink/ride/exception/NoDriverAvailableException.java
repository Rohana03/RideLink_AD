package lk.sliit.ridelink.ride.exception;

/** Mapped to 409 when no AVAILABLE driver is within the matching radius of the pickup. */
public class NoDriverAvailableException extends RuntimeException {
    public NoDriverAvailableException(double radiusKm) {
        super("No available driver within " + radiusKm + " km of the pickup location");
    }
}
