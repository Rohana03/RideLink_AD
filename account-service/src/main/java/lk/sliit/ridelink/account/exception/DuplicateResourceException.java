package lk.sliit.ridelink.account.exception;

/** Mapped to 409, e.g. an email that is already registered. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
