package lk.sliit.ridelink.ride.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lk.sliit.ridelink.ride.client.DriverServiceClient;
import lk.sliit.ridelink.ride.client.EligibleDriver;
import lk.sliit.ridelink.ride.client.FareEstimate;
import lk.sliit.ridelink.ride.client.FarePayment;
import lk.sliit.ridelink.ride.client.FareServiceClient;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Location;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.exception.ForbiddenRideAccessException;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.NoDriverAvailableException;
import lk.sliit.ridelink.ride.exception.RideNotFoundException;
import lk.sliit.ridelink.ride.messaging.RideCompletedEvent;
import lk.sliit.ridelink.ride.messaging.RideCompletionNotifier;
import lk.sliit.ridelink.ride.repository.RideRepository;

@Service
public class RideServiceImpl implements RideService {

    private static final Logger log = LoggerFactory.getLogger(RideServiceImpl.class);

    private static final Map<RideStatus, Set<RideStatus>> VALID_TRANSITIONS = new EnumMap<>(RideStatus.class);

    static {
        VALID_TRANSITIONS.put(RideStatus.REQUESTED, EnumSet.of(RideStatus.ASSIGNED, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.ASSIGNED, EnumSet.of(RideStatus.ACCEPTED, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.ACCEPTED, EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        VALID_TRANSITIONS.put(RideStatus.IN_PROGRESS, EnumSet.of(RideStatus.COMPLETED));
        VALID_TRANSITIONS.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        VALID_TRANSITIONS.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));
    }

    /** A driver is reserved (ON_TRIP) from assignment until the ride is completed or cancelled. */
    private static final Set<RideStatus> DRIVER_RESERVED = EnumSet.of(RideStatus.ASSIGNED, RideStatus.ACCEPTED);

    private final RideRepository rideRepository;
    private final DriverServiceClient driverServiceClient;
    private final FareServiceClient fareServiceClient;
    private final RideCompletionNotifier rideCompletionNotifier;
    private final double matchingRadiusKm;
    private final int maxCandidates;

    public RideServiceImpl(RideRepository rideRepository,
                           DriverServiceClient driverServiceClient,
                           FareServiceClient fareServiceClient,
                           RideCompletionNotifier rideCompletionNotifier,
                           @Value("${ridelink.matching.radius-km:5.0}") double matchingRadiusKm,
                           @Value("${ridelink.matching.max-candidates:5}") int maxCandidates) {
        this.rideRepository = rideRepository;
        this.driverServiceClient = driverServiceClient;
        this.fareServiceClient = fareServiceClient;
        this.rideCompletionNotifier = rideCompletionNotifier;
        this.matchingRadiusKm = matchingRadiusKm;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public RideResponse createRide(String passengerId, CreateRideRequest request) {
        Location pickup = request.pickupLocation().toEntity();
        Location destination = request.destinationLocation().toEntity();

        // Sync REST: the passenger sees the estimate in the response, so we need it now.
        FareEstimate estimate = fareServiceClient.estimate(pickup, destination, request.vehicleType());

        Ride ride = Ride.builder()
                .passengerId(passengerId)
                .vehicleType(request.vehicleType())
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .estimatedDistanceKm(estimate.distanceKm())
                .estimatedFare(estimate.totalFare().doubleValue())
                .currency(estimate.currency())
                .status(RideStatus.REQUESTED)
                .requestedAt(Instant.now())
                .build();
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse getRide(String rideId, RideCaller caller) {
        Ride ride = findRideOrThrow(rideId);
        if (!caller.admin() && !isPassenger(ride, caller) && !isAssignedDriver(ride, caller)) {
            throw new ForbiddenRideAccessException("You can only view your own rides");
        }
        return RideResponse.fromEntity(ride);
    }

    @Override
    public List<RideResponse> getMyRides(RideCaller caller) {
        return rideRepository.findByPassengerIdOrDriverUserIdOrderByRequestedAtDesc(caller.userId(), caller.userId())
                .stream().map(RideResponse::fromEntity).toList();
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

    /**
     * Documented assignment rule: ask Driver &amp; Vehicle for AVAILABLE drivers within the matching
     * radius (nearest first, same vehicle type if one was requested) and reserve the first one that
     * can still be moved to ON_TRIP. A 409 from Driver &amp; Vehicle means that driver was just taken
     * by another ride, so the next candidate is tried.
     */
    @Override
    public RideResponse assignDriver(String rideId, RideCaller caller) {
        Ride ride = findRideOrThrow(rideId);
        if (!caller.admin() && !isPassenger(ride, caller)) {
            throw new ForbiddenRideAccessException("Only the passenger of this ride can request a driver");
        }
        ensureCanTransition(ride, RideStatus.ASSIGNED);

        List<EligibleDriver> candidates = driverServiceClient.findEligibleDrivers(
                ride.getPickupLocation().getLatitude(), ride.getPickupLocation().getLongitude(),
                ride.getVehicleType(), matchingRadiusKm, maxCandidates);

        for (EligibleDriver candidate : candidates) {
            if (!driverServiceClient.markOnTrip(candidate.driverId())) {
                continue;
            }
            ride.setStatus(RideStatus.ASSIGNED);
            ride.setDriverId(candidate.driverId());
            ride.setDriverUserId(candidate.userId());
            ride.setDriverName(candidate.fullName());
            if (ride.getVehicleType() == null) {
                ride.setVehicleType(vehicleTypeOf(candidate));
            }
            ride.setAssignedAt(Instant.now());
            try {
                return RideResponse.fromEntity(rideRepository.save(ride));
            } catch (RuntimeException e) {
                // The ride was not saved (e.g. assigned concurrently), so give the driver back.
                releaseDriverQuietly(candidate.driverId());
                throw e;
            }
        }
        throw new NoDriverAvailableException(matchingRadiusKm);
    }

    @Override
    public RideResponse acceptRide(String rideId, RideCaller caller) {
        Ride ride = findRideOrThrow(rideId);
        ensureAssignedDriverOrAdmin(ride, caller);
        transition(ride, RideStatus.ACCEPTED);
        ride.setAcceptedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse startRide(String rideId, RideCaller caller) {
        Ride ride = findRideOrThrow(rideId);
        ensureAssignedDriverOrAdmin(ride, caller);
        transition(ride, RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    /**
     * The ride is completed even if a downstream call fails: the trip has physically ended.
     * A failed release is logged; a failed fare handoff leaves fareRecorded=false so it is visible.
     */
    @Override
    public RideResponse completeRide(String rideId, RideCaller caller, CompleteRideRequest request) {
        Ride ride = findRideOrThrow(rideId);
        ensureAssignedDriverOrAdmin(ride, caller);
        transition(ride, RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());

        releaseDriverQuietly(ride.getDriverId());

        try {
            Optional<FarePayment> payment = rideCompletionNotifier.notifyRideCompleted(toEvent(ride, request));
            payment.ifPresent(p -> {
                ride.setFinalFare(p.totalAmount().doubleValue());
                ride.setCurrency(p.currency());
            });
            ride.setFareRecorded(true);
        } catch (DownstreamServiceException e) {
            log.warn("Ride {} completed but the fare handoff failed: {}", rideId, e.getMessage());
            ride.setFareRecorded(false);
        }
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    @Override
    public RideResponse cancelRide(String rideId, RideCaller caller, CancelRideRequest request) {
        Ride ride = findRideOrThrow(rideId);
        if (!caller.admin() && !isPassenger(ride, caller) && !isAssignedDriver(ride, caller)) {
            throw new ForbiddenRideAccessException("You can only cancel your own rides");
        }
        boolean driverWasReserved = DRIVER_RESERVED.contains(ride.getStatus()) && ride.getDriverId() != null;
        transition(ride, RideStatus.CANCELLED);
        ride.setCancellationReason(request.reason());
        ride.setCancelledAt(Instant.now());
        if (driverWasReserved) {
            releaseDriverQuietly(ride.getDriverId());
        }
        return RideResponse.fromEntity(rideRepository.save(ride));
    }

    private Ride findRideOrThrow(String rideId) {
        return rideRepository.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private void ensureCanTransition(Ride ride, RideStatus target) {
        RideStatus current = ride.getStatus();
        if (!VALID_TRANSITIONS.get(current).contains(target)) {
            throw new InvalidRideStatusTransitionException(current, target);
        }
    }

    private void transition(Ride ride, RideStatus target) {
        ensureCanTransition(ride, target);
        ride.setStatus(target);
    }

    private void ensureAssignedDriverOrAdmin(Ride ride, RideCaller caller) {
        if (!caller.admin() && !isAssignedDriver(ride, caller)) {
            throw new ForbiddenRideAccessException("Only the driver assigned to this ride can do this");
        }
    }

    private static boolean isPassenger(Ride ride, RideCaller caller) {
        return caller.userId() != null && caller.userId().equals(ride.getPassengerId());
    }

    private static boolean isAssignedDriver(Ride ride, RideCaller caller) {
        return caller.userId() != null && caller.userId().equals(ride.getDriverUserId());
    }

    private void releaseDriverQuietly(String driverId) {
        if (driverId == null) {
            return;
        }
        try {
            driverServiceClient.markAvailable(driverId);
        } catch (DownstreamServiceException e) {
            log.warn("Could not set driver {} back to AVAILABLE: {}", driverId, e.getMessage());
        }
    }

    private static VehicleType vehicleTypeOf(EligibleDriver driver) {
        if (driver.vehicle() == null || driver.vehicle().vehicleType() == null) {
            return null;
        }
        try {
            return VehicleType.valueOf(driver.vehicle().vehicleType());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static RideCompletedEvent toEvent(Ride ride, CompleteRideRequest request) {
        return new RideCompletedEvent(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                ride.getDriverUserId(),
                ride.getVehicleType(),
                ride.getPickupLocation().getLatitude(),
                ride.getPickupLocation().getLongitude(),
                ride.getDestinationLocation().getLatitude(),
                ride.getDestinationLocation().getLongitude(),
                request.actualDistanceKm(),
                request.actualDurationMinutes(),
                LocalDateTime.ofInstant(ride.getCompletedAt(), ZoneId.systemDefault()));
    }
}
