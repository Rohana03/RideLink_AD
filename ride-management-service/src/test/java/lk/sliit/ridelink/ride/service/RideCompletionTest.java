package lk.sliit.ridelink.ride.service;

import static lk.sliit.ridelink.ride.service.RideServiceImplTest.DRIVER;
import static lk.sliit.ridelink.ride.service.RideServiceImplTest.PASSENGER;
import static lk.sliit.ridelink.ride.service.RideServiceImplTest.requestedRide;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lk.sliit.ridelink.ride.client.DriverServiceClient;
import lk.sliit.ridelink.ride.client.FarePayment;
import lk.sliit.ridelink.ride.client.FareServiceClient;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.LocationDto;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.exception.ForbiddenRideAccessException;
import lk.sliit.ridelink.ride.messaging.RideCompletedEvent;
import lk.sliit.ridelink.ride.messaging.RideCompletionNotifier;
import lk.sliit.ridelink.ride.repository.RideRepository;

/** Completion hand-off to Fare &amp; Payment, driver release, cancellation, and downstream failures. */
@ExtendWith(MockitoExtension.class)
class RideCompletionTest {

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
        ride.setDriverId("driver-1");
        ride.setDriverUserId("driver-user-1");
        ride.setVehicleType(VehicleType.VAN);
    }

    private void rideIs(RideStatus status) {
        ride.setStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
    }

    private void saveReturnsArgument() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void completionSendsTheRideAndMeasuredTripToFareAndPayment() {
        rideIs(RideStatus.IN_PROGRESS);
        when(rideCompletionNotifier.notifyRideCompleted(any()))
                .thenReturn(Optional.of(new FarePayment("ride-1", new BigDecimal("1350.00"), "LKR", "PENDING")));
        saveReturnsArgument();

        RideResponse response = rideService.completeRide("ride-1", DRIVER, new CompleteRideRequest(10.0, 20.0));

        ArgumentCaptor<RideCompletedEvent> event = ArgumentCaptor.forClass(RideCompletedEvent.class);
        verify(rideCompletionNotifier).notifyRideCompleted(event.capture());
        assertThat(event.getValue().rideId()).isEqualTo("ride-1");
        assertThat(event.getValue().passengerId()).isEqualTo("passenger-1");
        assertThat(event.getValue().driverUserId()).isEqualTo("driver-user-1");
        assertThat(event.getValue().vehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(event.getValue().actualDistanceKm()).isEqualTo(10.0);
        assertThat(event.getValue().actualDurationMinutes()).isEqualTo(20.0);
        assertThat(event.getValue().dropoffLatitude()).isEqualTo(6.9147);
        assertThat(event.getValue().completedAt()).isNotNull();
        assertThat(response.finalFare()).isEqualTo(1350.0);
        assertThat(response.fareRecorded()).isTrue();
    }

    @Test
    void completionReleasesTheDriver() {
        rideIs(RideStatus.IN_PROGRESS);
        when(rideCompletionNotifier.notifyRideCompleted(any())).thenReturn(Optional.empty());
        saveReturnsArgument();

        rideService.completeRide("ride-1", DRIVER, CompleteRideRequest.empty());

        verify(driverServiceClient).markAvailable("driver-1");
    }

    @Test
    void asyncHandoffCompletesWithTheFinalFareLeftToFareAndPayment() {
        rideIs(RideStatus.IN_PROGRESS);
        when(rideCompletionNotifier.notifyRideCompleted(any())).thenReturn(Optional.empty());
        saveReturnsArgument();

        RideResponse response = rideService.completeRide("ride-1", DRIVER, CompleteRideRequest.empty());

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.finalFare()).isNull();
        assertThat(response.fareRecorded()).isTrue();
    }

    @Test
    void fareServiceDownStillCompletesTheRideButFlagsIt() {
        rideIs(RideStatus.IN_PROGRESS);
        when(rideCompletionNotifier.notifyRideCompleted(any()))
                .thenThrow(new DownstreamServiceException("Fare & Payment Service is unavailable", null));
        saveReturnsArgument();

        RideResponse response = rideService.completeRide("ride-1", DRIVER, CompleteRideRequest.empty());

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.fareRecorded()).isFalse();
        verify(driverServiceClient).markAvailable("driver-1");
    }

    @Test
    void driverServiceDownStillCompletesTheRide() {
        rideIs(RideStatus.IN_PROGRESS);
        doThrow(new DownstreamServiceException("Driver & Vehicle Service is unavailable", null))
                .when(driverServiceClient).markAvailable("driver-1");
        when(rideCompletionNotifier.notifyRideCompleted(any())).thenReturn(Optional.empty());
        saveReturnsArgument();

        assertThat(rideService.completeRide("ride-1", DRIVER, CompleteRideRequest.empty()).status())
                .isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void thePassengerCannotCompleteTheRide() {
        rideIs(RideStatus.IN_PROGRESS);

        assertThatThrownBy(() -> rideService.completeRide("ride-1", PASSENGER, CompleteRideRequest.empty()))
                .isInstanceOf(ForbiddenRideAccessException.class);
        verify(rideCompletionNotifier, never()).notifyRideCompleted(any());
    }

    @Test
    void cancellingAnAssignedRideReleasesTheDriver() {
        rideIs(RideStatus.ASSIGNED);
        saveReturnsArgument();

        RideResponse response = rideService.cancelRide("ride-1", PASSENGER, new CancelRideRequest("Found another ride"));

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        verify(driverServiceClient).markAvailable("driver-1");
    }

    @Test
    void cancellingARequestedRideContactsNoDriver() {
        ride.setDriverId(null);
        ride.setDriverUserId(null);
        rideIs(RideStatus.REQUESTED);
        saveReturnsArgument();

        rideService.cancelRide("ride-1", PASSENGER, new CancelRideRequest(null));

        verify(driverServiceClient, never()).markAvailable(any());
    }

    @Test
    void aStrangerCannotCancelTheRide() {
        rideIs(RideStatus.ASSIGNED);

        assertThatThrownBy(() -> rideService.cancelRide("ride-1", new RideCaller("stranger", false),
                new CancelRideRequest(null)))
                .isInstanceOf(ForbiddenRideAccessException.class);
    }

    @Test
    void fareServiceDownWhenRequestingARideSavesNothing() {
        when(fareServiceClient.estimate(any(), any(), any()))
                .thenThrow(new DownstreamServiceException("Fare & Payment Service is unavailable", null));

        assertThatThrownBy(() -> rideService.createRide("passenger-1", new CreateRideRequest(
                new LocationDto("Home", 6.9271, 79.8612), new LocationDto("Office", 6.9147, 79.8774), null)))
                .isInstanceOf(DownstreamServiceException.class);
        verify(rideRepository, never()).save(any());
    }
}
