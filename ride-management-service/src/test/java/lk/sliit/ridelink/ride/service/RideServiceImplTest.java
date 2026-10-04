package lk.sliit.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lk.sliit.ridelink.ride.client.DriverServiceClient;
import lk.sliit.ridelink.ride.client.EligibleDriver;
import lk.sliit.ridelink.ride.client.FareEstimate;
import lk.sliit.ridelink.ride.client.FarePayment;
import lk.sliit.ridelink.ride.client.FareServiceClient;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.LocationDto;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.RideNotFoundException;
import lk.sliit.ridelink.ride.messaging.RideCompletionNotifier;
import lk.sliit.ridelink.ride.repository.RideRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceImplTest {

    static final RideCaller PASSENGER = new RideCaller("passenger-1", false);
    static final RideCaller DRIVER = new RideCaller("driver-user-1", false);

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FareServiceClient fareServiceClient;

    @Mock
    private RideCompletionNotifier rideCompletionNotifier;

    private RideServiceImpl rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideServiceImpl(rideRepository, driverServiceClient, fareServiceClient,
                rideCompletionNotifier, 5.0, 5);
    }

    static Ride requestedRide() {
        return Ride.builder()
                .id("ride-1")
                .passengerId("passenger-1")
                .pickupLocation(new LocationDto("Home", 6.9271, 79.8612).toEntity())
                .destinationLocation(new LocationDto("Office", 6.9147, 79.8774).toEntity())
                .status(RideStatus.REQUESTED)
                .build();
    }

    static EligibleDriver nearbyDriver(String driverId, String userId, double distanceKm) {
        return new EligibleDriver(driverId, userId, "Nimal Perera", distanceKm,
                new EligibleDriver.Vehicle("CAR", "Toyota", "Aqua", "WP CAB-1234"));
    }

    private void saveReturnsArgument() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRide_savesRideInRequestedStatusWithFareServiceEstimate() {
        when(fareServiceClient.estimate(any(), any(), isNull()))
                .thenReturn(new FareEstimate(2.13, 4.26, new BigDecimal("270.40"), "LKR"));
        saveReturnsArgument();
        CreateRideRequest request = new CreateRideRequest(
                new LocationDto("Home", 6.9271, 79.8612),
                new LocationDto("Office", 6.9147, 79.8774),
                null);

        RideResponse response = rideService.createRide("passenger-1", request);

        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.requestedAt()).isNotNull();
        assertThat(response.estimatedFare()).isEqualTo(270.40);
        assertThat(response.estimatedDistanceKm()).isEqualTo(2.13);
        assertThat(response.currency()).isEqualTo("LKR");
        ArgumentCaptor<Ride> captor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(captor.capture());
        assertThat(captor.getValue().getPassengerId()).isEqualTo("passenger-1");
    }

    @Test
    void getRide_throwsWhenMissing() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.getRide("missing", PASSENGER))
                .isInstanceOf(RideNotFoundException.class);
    }

    @Test
    void assignDriver_movesRequestedToAssigned() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.findEligibleDrivers(anyDouble(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(nearbyDriver("driver-1", "driver-user-1", 0.8)));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(true);
        saveReturnsArgument();

        RideResponse response = rideService.assignDriver("ride-1", PASSENGER);

        assertThat(response.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.assignedAt()).isNotNull();
    }

    @Test
    void acceptRide_rejectsWhenRideStillOnlyRequested() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.acceptRide("ride-1", new RideCaller("admin-1", true)))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
    }

    @Test
    void fullLifecycle_requestedToCompleted() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.findEligibleDrivers(anyDouble(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(nearbyDriver("driver-1", "driver-user-1", 0.8)));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(true);
        when(rideCompletionNotifier.notifyRideCompleted(any()))
                .thenReturn(Optional.of(new FarePayment("ride-1", new BigDecimal("1250.00"), "LKR", "PENDING")));
        saveReturnsArgument();

        rideService.assignDriver("ride-1", PASSENGER);
        rideService.acceptRide("ride-1", DRIVER);
        rideService.startRide("ride-1", DRIVER);
        RideResponse completed = rideService.completeRide("ride-1", DRIVER, CompleteRideRequest.empty());

        assertThat(completed.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(completed.finalFare()).isEqualTo(1250.0);
        assertThat(completed.fareRecorded()).isTrue();
        assertThat(completed.completedAt()).isNotNull();
        verify(driverServiceClient).markAvailable("driver-1");
    }

    @Test
    void completeRide_rejectedFromRequestedStatus() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide("ride-1", new RideCaller("admin-1", true),
                CompleteRideRequest.empty()))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
    }

    @Test
    void cancelRide_allowedFromRequested() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        saveReturnsArgument();

        RideResponse response = rideService.cancelRide("ride-1", PASSENGER, new CancelRideRequest("Passenger changed plans"));

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Passenger changed plans");
    }

    @Test
    void cancelRide_rejectedOnceCompleted() {
        Ride ride = requestedRide();
        ride.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("ride-1", PASSENGER, new CancelRideRequest("too late")))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
    }

    @Test
    void getRides_filtersByPassengerId() {
        Ride ride = requestedRide();
        when(rideRepository.findByPassengerId("passenger-1")).thenReturn(List.of(ride));

        List<RideResponse> responses = rideService.getRides("passenger-1", null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).passengerId()).isEqualTo("passenger-1");
    }
}
