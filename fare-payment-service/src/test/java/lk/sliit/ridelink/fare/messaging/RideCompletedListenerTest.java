package lk.sliit.ridelink.fare.messaging;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RideCompletedListenerTest {

    @Mock
    private PaymentService paymentService;

    private RideCompletedListener listener;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        listener = new RideCompletedListener(paymentService, validator);
    }

    @Test
    @DisplayName("A valid event is handed to the payment service")
    void validEventIsProcessed() {
        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId("ride-001")
                .passengerId("acc-psg-001")
                .pickupLatitude(6.9271).pickupLongitude(79.8612)
                .dropoffLatitude(6.9000).dropoffLongitude(79.8500)
                .build();

        listener.onRideCompleted(event);

        verify(paymentService).recordCompletedRide(event);
    }

    @Test
    @DisplayName("An invalid event is rejected without requeue and never reaches the payment service")
    void invalidEventIsRejected() {
        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId("ride-001")
                .pickupLatitude(123.0)
                .build();

        AmqpRejectAndDontRequeueException ex = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.onRideCompleted(event));

        assertTrue(ex.getMessage().contains("passengerId"));
        verifyNoInteractions(paymentService);
    }
}
