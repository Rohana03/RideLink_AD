package lk.sliit.ridelink.ride.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;

@ExtendWith(MockitoExtension.class)
class RabbitRideCompletionNotifierTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final RideCompletedEvent event = new RideCompletedEvent("ride-1", "passenger-1", "driver-1",
            "driver-user-1", VehicleType.CAR, 6.9271, 79.8612, 6.9147, 79.8774, null, null, LocalDateTime.now());

    @Test
    void publishesToTheRideEventsExchangeWithTheCompletedRoutingKey() {
        RabbitRideCompletionNotifier notifier =
                new RabbitRideCompletionNotifier(rabbitTemplate, "ride.events.exchange", "ride.completed");

        assertThat(notifier.notifyRideCompleted(event)).isEmpty();
        verify(rabbitTemplate).convertAndSend("ride.events.exchange", "ride.completed", event);
    }

    @Test
    void brokerDownBecomesDownstreamServiceException() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        RabbitRideCompletionNotifier notifier =
                new RabbitRideCompletionNotifier(rabbitTemplate, "ride.events.exchange", "ride.completed");

        assertThatThrownBy(() -> notifier.notifyRideCompleted(event))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("Message broker is unavailable");
    }
}
