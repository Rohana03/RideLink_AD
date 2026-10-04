package lk.sliit.ridelink.ride.service;

import java.util.List;

import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.RideStatus;

public interface RideService {

    /** Creates a REQUESTED ride with an estimate from the Fare &amp; Payment Service. */
    RideResponse createRide(String passengerId, CreateRideRequest request);

    /** Visible to the ride's passenger, its assigned driver, or an admin. */
    RideResponse getRide(String rideId, RideCaller caller);

    /** Rides where the caller is the passenger or the assigned driver. */
    List<RideResponse> getMyRides(RideCaller caller);

    /** Admin listing; filters are optional. */
    List<RideResponse> getRides(String passengerId, String driverId, RideStatus status);

    /** Assigns the nearest eligible driver from the Driver &amp; Vehicle Service and marks them ON_TRIP. */
    RideResponse assignDriver(String rideId, RideCaller caller);

    RideResponse acceptRide(String rideId, RideCaller caller);

    RideResponse startRide(String rideId, RideCaller caller);

    /** Completes the ride, releases the driver and hands the ride to Fare &amp; Payment for the final fare. */
    RideResponse completeRide(String rideId, RideCaller caller, CompleteRideRequest request);

    RideResponse cancelRide(String rideId, RideCaller caller, CancelRideRequest request);
}
