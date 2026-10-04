package lk.sliit.ridelink.fare.exception;

/** Mapped to 409, e.g. paying a ride that is already PAID or asking for a receipt before payment. */
public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
