package lk.sliit.ridelink.ride.exception;

/**
 * Mapped to 503 when another RideLink service is unreachable or answers unexpectedly,
 * so the caller gets a clear message instead of a stack trace.
 */
public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
