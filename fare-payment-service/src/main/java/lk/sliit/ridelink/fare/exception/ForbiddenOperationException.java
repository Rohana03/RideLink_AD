package lk.sliit.ridelink.fare.exception;

/** Mapped to 403 when a signed-in user acts on a payment that is not theirs. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
