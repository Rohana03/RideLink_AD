package lk.sliit.ridelink.ride.service;

/** Who is calling, from the verified JWT: the Account Service userId and whether they are an admin. */
public record RideCaller(String userId, boolean admin) {
}
