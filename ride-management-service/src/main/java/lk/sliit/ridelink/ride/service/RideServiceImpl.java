package lk.sliit.ridelink.ride.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lk.sliit.ridelink.ride.dto.AssignDriverRequest;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.RideNotFoundException;
import lk.sliit.ridelink.ride.repository.RideRepository;

@Service
public class RideServiceImpl implements RideService {

    private static final Map<RideStatus, Set<RideStatus>> VALID_TRANSITIONS = new EnumMap<>(RideStatus.class);

    static {
        VALID_TRANSITIONS.put(RideStatus.REQUESTED, EnumSet.of(RideStatus.ASSIGNED, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.ASSIGNED, EnumSet.of(RideStatus.ACCEPTED, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.ACCEPTED, EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.IN_PROGRESS, EnumSet.of(RideStatus.COMPLETED));
        VALID_TRANSITIONS.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        VALID_TRANSITIONS.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));
    }

    private final RideRepository rideRepository;

    public RideServiceImpl(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    @Override
    public RideResponse createRide(CreateRideRequest request) {
        Ride ride = Ride.builder()
                .passengerId(request.passengerId())
                .pickupLocation(request.pickupLocation().toEntity())
                .destinationLocation(request.destinationLocation().toEntity())
                .estimatedFare(request.estimatedFare())
                .status(RideStatus.REQUESTED)
                .requestedAt(Instant.now())
                .build();
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse getRide(String rideId) {
        return RideResponse.fromEntity(findRideOrThrow(rideId));
    }

    @Override
    public List<RideResponse> getRides(String passengerId, String driverId, RideStatus status) {
        List<Ride> rides;
        if (passengerId != null) {
            rides = rideRepository.findByPassengerId(passengerId);
        } else if (driverId != null) {
            rides = rideRepository.findByDriverId(driverId);
        } else if (status != null) {
            rides = rideRepository.findByStatus(status);
        } else {
            rides = rideRepository.findAll();
        }
        return rides.stream().map(RideResponse::fromEntity).toList();
    }

    @Override
    public RideResponse assignDriver(String rideId, AssignDriverRequest request) {
        Ride ride = findRideOrThrow(rideId);
        transition(ride, RideStatus.ASSIGNED);
        ride.setDriverId(request.driverId());
        ride.setAssignedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse acceptRide(String rideId) {
        Ride ride = findRideOrThrow(rideId);
        transition(ride, RideStatus.ACCEPTED);
        ride.setAcceptedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse startRide(String rideId) {
        Ride ride = findRideOrThrow(rideId);
        transition(ride, RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse completeRide(String rideId, CompleteRideRequest request) {
        Ride ride = findRideOrThrow(rideId);
        transition(ride, RideStatus.COMPLETED);
        ride.setFinalFare(request.finalFare());
        ride.setCompletedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse cancelRide(String rideId, CancelRideRequest request) {
        Ride ride = findRideOrThrow(rideId);
        transition(ride, RideStatus.CANCELLED);
        ride.setCancellationReason(request.reason());
        ride.setCancelledAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    private Ride findRideOrThrow(String rideId) {
        return rideRepository.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private void transition(Ride ride, RideStatus target) {
        RideStatus current = ride.getStatus();
        if (!VALID_TRANSITIONS.get(current).contains(target)) {
            throw new InvalidRideStatusTransitionException(current, target);
        }
        ride.setStatus(target);
    }
}
