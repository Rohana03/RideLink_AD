package lk.sliit.ridelink.account.entity;

/**
 * Account roles. They are written to the JWT "roles" claim without a "ROLE_" prefix;
 * each service adds the prefix when it builds Spring Security authorities.
 */
public enum Role {
    PASSENGER,
    DRIVER,
    ADMIN
}
