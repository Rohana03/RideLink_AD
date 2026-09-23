package lk.sliit.ridelink.fare.entity;

/**
 * Same values as the Driver &amp; Vehicle Service's VehicleType, so a vehicle type can be
 * passed between the services by name. Each type has a fare multiplier in application.yml.
 */
public enum VehicleType {
    CAR,
    VAN,
    SUV,
    SEDAN,
    TUK_TUK,
    BIKE
}
