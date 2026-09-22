package lk.sliit.ridelink.account.entity;

/**
 * Lifecycle of an account. Only ACTIVE accounts can log in or change their profile.
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
