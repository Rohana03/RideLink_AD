package lk.sliit.ridelink.ride.service;

import java.util.List;

import lk.sliit.ridelink.ride.dto.AssignDriverRequest;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.RideStatus;

public interface RideService {

    RideResponse createRide(CreateRideRequest request);

    RideResponse getRide(String rideId);

    List<RideResponse> getRides(String passengerId, String driverId, RideStatus status);

    RideResponse assignDriver(String rideId, AssignDriverRequest request);

    RideResponse acceptRide(String rideId);

    RideResponse startRide(String rideId);

    RideResponse completeRide(String rideId, CompleteRideRequest request);

    RideResponse cancelRide(String rideId, CancelRideRequest request);
}
