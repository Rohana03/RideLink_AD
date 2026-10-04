package lk.sliit.ridelink.ride.service;

import static lk.sliit.ridelink.ride.service.RideServiceImplTest.PASSENGER;
import static lk.sliit.ridelink.ride.service.RideServiceImplTest.nearbyDriver;
import static lk.sliit.ridelink.ride.service.RideServiceImplTest.requestedRide;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import lk.sliit.ridelink.ride.client.DriverServiceClient;
import lk.sliit.ridelink.ride.client.EligibleDriver;
import lk.sliit.ridelink.ride.client.FareServiceClient;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.exception.ForbiddenRideAccessException;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.NoDriverAvailableException;
import lk.sliit.ridelink.ride.messaging.RideCompletionNotifier;
import lk.sliit.ridelink.ride.repository.RideRepository;

/** Driver assignment against the Driver &amp; Vehicle Service, and who may request it. */
@ExtendWith(MockitoExtension.class)
class RideAssignmentTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FareServiceClient fareServiceClient;

    @Mock
    private RideCompletionNotifier rideCompletionNotifier;

    private RideServiceImpl rideService;
    private Ride ride;

    @BeforeEach
    void setUp() {
        rideService = new RideServiceImpl(rideRepository, driverServiceClient, fareServiceClient,
                rideCompletionNotifier, 5.0, 5);
        ride = requestedRide();
    }

    private void rideExists() {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
    }

    private void eligibleDriversAre(EligibleDriver... drivers) {
        when(driverServiceClient.findEligibleDrivers(anyDouble(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(drivers));
    }

    @Test
    void assignsTheNearestDriverAndRecordsTheirAccount() {
        rideExists();
        eligibleDriversAre(nearbyDriver("driver-1", "driver-user-1", 0.8), nearbyDriver("driver-2", "driver-user-2", 2.4));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(true);
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.assignDriver("ride-1", PASSENGER);

        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.driverUserId()).isEqualTo("driver-user-1");
        assertThat(response.driverName()).isEqualTo("Nimal Perera");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR);
        verify(driverServiceClient, never()).markOnTrip("driver-2");
    }

    @Test
    void searchesAtThePickupWithTheConfiguredRadiusAndRequestedVehicleType() {
        ride.setVehicleType(VehicleType.VAN);
        rideExists();
        eligibleDriversAre();

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(NoDriverAvailableException.class);

        verify(driverServiceClient).findEligibleDrivers(6.9271, 79.8612, VehicleType.VAN, 5.0, 5);
    }

    @Test
    void skipsADriverThatWasTakenByAnotherRide() {
        rideExists();
        eligibleDriversAre(nearbyDriver("driver-1", "driver-user-1", 0.8), nearbyDriver("driver-2", "driver-user-2", 2.4));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(false);
        when(driverServiceClient.markOnTrip("driver-2")).thenReturn(true);
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.assignDriver("ride-1", PASSENGER);

        assertThat(response.driverId()).isEqualTo("driver-2");
    }

    @Test
    void noEligibleDriverGivesNoDriverAvailableAndLeavesTheRideRequested() {
        rideExists();
        eligibleDriversAre();

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(NoDriverAvailableException.class)
                .hasMessageContaining("5.0 km");

        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void everyCandidateTakenAlsoGivesNoDriverAvailable() {
        rideExists();
        eligibleDriversAre(nearbyDriver("driver-1", "driver-user-1", 0.8));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(false);

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(NoDriverAvailableException.class);
    }

    @Test
    void driverServiceDownIsReportedAndNothingIsSaved() {
        rideExists();
        when(driverServiceClient.findEligibleDrivers(anyDouble(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenThrow(new DownstreamServiceException("Driver & Vehicle Service is unavailable", null));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(DownstreamServiceException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void driverIsReleasedIfTheRideCannotBeSaved() {
        rideExists();
        eligibleDriversAre(nearbyDriver("driver-1", "driver-user-1", 0.8));
        when(driverServiceClient.markOnTrip("driver-1")).thenReturn(true);
        when(rideRepository.save(any(Ride.class))).thenThrow(new OptimisticLockingFailureException("assigned concurrently"));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(driverServiceClient).markAvailable("driver-1");
    }

    @Test
    void anotherPassengerCannotRequestADriverForThisRide() {
        rideExists();

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", new RideCaller("passenger-2", false)))
                .isInstanceOf(ForbiddenRideAccessException.class);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void anAssignedRideCannotBeAssignedAgainAndNoDriverIsContacted() {
        ride.setStatus(RideStatus.ASSIGNED);
        rideExists();

        assertThatThrownBy(() -> rideService.assignDriver("ride-1", PASSENGER))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void onlyTheAssignedDriverCanAcceptStartOrComplete() {
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setDriverUserId("driver-user-1");
        rideExists();
        RideCaller otherDriver = new RideCaller("driver-user-9", false);

        assertThatThrownBy(() -> rideService.acceptRide("ride-1", otherDriver))
                .isInstanceOf(ForbiddenRideAccessException.class);
        assertThatThrownBy(() -> rideService.startRide("ride-1", otherDriver))
                .isInstanceOf(ForbiddenRideAccessException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void viewingIsLimitedToThePassengerTheDriverAndAdmins() {
        ride.setDriverUserId("driver-user-1");
        rideExists();

        assertThat(rideService.getRide("ride-1", PASSENGER).id()).isEqualTo("ride-1");
        assertThat(rideService.getRide("ride-1", new RideCaller("driver-user-1", false)).id()).isEqualTo("ride-1");
        assertThat(rideService.getRide("ride-1", new RideCaller("admin-1", true)).id()).isEqualTo("ride-1");
        assertThatThrownBy(() -> rideService.getRide("ride-1", new RideCaller("stranger", false)))
                .isInstanceOf(ForbiddenRideAccessException.class);
    }

    @Test
    void myRidesCoversBothSidesOfARide() {
        when(rideRepository.findByPassengerIdOrDriverUserIdOrderByRequestedAtDesc(eq("driver-user-1"), anyString()))
                .thenReturn(List.of(ride));

        assertThat(rideService.getMyRides(new RideCaller("driver-user-1", false))).hasSize(1);
    }
}
