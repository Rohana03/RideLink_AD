package lk.sliit.ridelink.ride.messaging;

import java.util.Optional;

import lk.sliit.ridelink.ride.client.FarePayment;

/**
 * Hands a completed ride to the Fare &amp; Payment Service. Two implementations, chosen by
 * ridelink.messaging.enabled: REST (default, returns the final fare immediately) or RabbitMQ
 * (asynchronous, the final fare is available from Fare &amp; Payment once it consumes the event).
 */
public interface RideCompletionNotifier {

    /**
     * @return the payment Fare &amp; Payment opened, when known synchronously
     * @throws lk.sliit.ridelink.ride.exception.DownstreamServiceException if the handoff failed
     */
    Optional<FarePayment> notifyRideCompleted(RideCompletedEvent event);
}
