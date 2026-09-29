package lk.sliit.ridelink.ride.messaging;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lk.sliit.ridelink.ride.client.FarePayment;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;

/**
 * Asynchronous handoff: publishes RideCompletedEvent and returns at once. The driver's
 * "complete ride" call no longer waits for payment set-up, and the durable queue keeps the
 * event if Fare &amp; Payment is briefly down.
 */
@Component
@ConditionalOnProperty(prefix = "ridelink.messaging", name = "enabled", havingValue = "true")
public class RabbitRideCompletionNotifier implements RideCompletionNotifier {

    private static final Logger log = LoggerFactory.getLogger(RabbitRideCompletionNotifier.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitRideCompletionNotifier(
            RabbitTemplate rabbitTemplate,
            @Value("${ridelink.rabbit.ride-events-exchange}") String exchange,
            @Value("${ridelink.rabbit.ride-completed-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public Optional<FarePayment> notifyRideCompleted(RideCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("Published RideCompletedEvent for ride {}", event.rideId());
            return Optional.empty();
        } catch (AmqpException e) {
            throw new DownstreamServiceException("Message broker is unavailable (RideCompletedEvent not published)", e);
        }
    }
}
