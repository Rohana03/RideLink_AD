package lk.sliit.ridelink.account.exception;

/** Mapped to 401. The message never reveals whether the email or the password was wrong. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
