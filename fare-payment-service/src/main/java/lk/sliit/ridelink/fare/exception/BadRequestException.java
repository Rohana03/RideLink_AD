package lk.sliit.ridelink.fare.exception;

/** Mapped to 400 for business-rule violations that bean validation cannot express. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
