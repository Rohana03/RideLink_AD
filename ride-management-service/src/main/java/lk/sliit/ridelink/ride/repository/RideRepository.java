package lk.sliit.ridelink.ride.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;

public interface RideRepository extends MongoRepository<Ride, String> {

    List<Ride> findByPassengerId(String passengerId);

    List<Ride> findByDriverId(String driverId);

    List<Ride> findByStatus(RideStatus status);
}
