package lk.sliit.ridelink.account.exception;

/** Mapped to 403 when a SUSPENDED or DEACTIVATED account tries to log in or change its profile. */
public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
