package lk.sliit.ridelink.fare.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumes RideCompletedEvent from ride.completed.queue. The ride is already complete by
 * the time this runs, so payment set-up never slows down the driver's "complete ride" call.
 */
@Component
@ConditionalOnProperty(prefix = "ridelink.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RideCompletedListener {

    private final PaymentService paymentService;
    private final Validator validator;

    @RabbitListener(queues = "${ridelink.rabbit.ride-completed-queue}")
    public void onRideCompleted(RideCompletedEvent event) {
        Set<ConstraintViolation<RideCompletedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String problems = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            log.warn("Rejecting invalid RideCompletedEvent: {}", problems);
            // An invalid message will never succeed, so drop it rather than redeliver it forever.
            throw new AmqpRejectAndDontRequeueException("Invalid RideCompletedEvent: " + problems);
        }

        log.info("Received RideCompletedEvent for ride {}", event.getRideId());
        paymentService.recordCompletedRide(event);
    }
}
