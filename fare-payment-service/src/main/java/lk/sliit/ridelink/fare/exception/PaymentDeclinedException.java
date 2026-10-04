package lk.sliit.ridelink.fare.exception;

/** Mapped to 402 when the simulated payment is declined. The FAILED attempt is already saved. */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
