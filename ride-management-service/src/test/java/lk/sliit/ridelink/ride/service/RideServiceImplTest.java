package lk.sliit.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lk.sliit.ridelink.ride.dto.AssignDriverRequest;
import lk.sliit.ridelink.ride.dto.CancelRideRequest;
import lk.sliit.ridelink.ride.dto.CompleteRideRequest;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.LocationDto;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.Ride;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.RideNotFoundException;
import lk.sliit.ridelink.ride.repository.RideRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceImplTest {

    @Mock
    private RideRepository rideRepository;

    private RideServiceImpl rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideServiceImpl(rideRepository);
    }

    private Ride requestedRide() {
        return Ride.builder()
                .id("ride-1")
                .passengerId("passenger-1")
                .pickupLocation(new LocationDto("Home", 6.9271, 79.8612).toEntity())
                .destinationLocation(new LocationDto("Office", 6.9147, 79.8774).toEntity())
                .status(RideStatus.REQUESTED)
                .build();
    }

    @Test
    void createRide_savesRideInRequestedStatus() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRideRequest request = new CreateRideRequest(
                "passenger-1",
                new LocationDto("Home", 6.9271, 79.8612),
                new LocationDto("Office", 6.9147, 79.8774),
                null);

        RideResponse response = rideService.createRide(request);

        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.requestedAt()).isNotNull();

        ArgumentCaptor<Ride> captor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(captor.capture());
        assertThat(captor.getValue().getPassengerId()).isEqualTo("passenger-1");
    }

    @Test
    void getRide_throwsWhenMissing() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.getRide("missing"))
                .isInstanceOf(RideNotFoundException.class);
    }

    @Test
    void assignDriver_movesRequestedToAssigned() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.assignDriver("ride-1", new AssignDriverRequest("driver-1"));

        assertThat(response.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.assignedAt()).isNotNull();
    }

    @Test
    void acceptRide_rejectsWhenRideStillOnlyRequested() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.acceptRide("ride-1"))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
    }

    @Test
    void fullLifecycle_requestedToCompleted() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        rideService.assignDriver("ride-1", new AssignDriverRequest("driver-1"));
        rideService.acceptRide("ride-1");
        rideService.startRide("ride-1");
        RideResponse completed = rideService.completeRide("ride-1", new CompleteRideRequest(1250.0));

        assertThat(completed.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(completed.finalFare()).isEqualTo(1250.0);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void completeRide_rejectedFromRequestedStatus() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide("ride-1", new CompleteRideRequest(1000.0)))
                .isInstanceOf(InvalidRideStatusTransitionException.class);
    }

    @Test
    void cancelRide_allowedFromRequested() {
        Ride ride = requestedRide();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.cancelRide("ride-1", new CancelRideRequest("Passenger changed plans"));

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Passenger changed plans");
    }

    @Test
    void cancelRide_rejectedOnceCompleted() {
        Ride ride = requestedRide();
        ride.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("ride-1", new CancelRideRequest("too late")))
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
