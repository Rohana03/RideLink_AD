package lk.sliit.ridelink.ride.messaging;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lk.sliit.ridelink.ride.client.FarePayment;
import lk.sliit.ridelink.ride.client.FareServiceClient;

/** Default handoff: POST the event to Fare &amp; Payment and get the final fare back straight away. */
@Component
@ConditionalOnProperty(prefix = "ridelink.messaging", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RestRideCompletionNotifier implements RideCompletionNotifier {

    private final FareServiceClient fareServiceClient;

    public RestRideCompletionNotifier(FareServiceClient fareServiceClient) {
        this.fareServiceClient = fareServiceClient;
    }

    @Override
    public Optional<FarePayment> notifyRideCompleted(RideCompletedEvent event) {
        return Optional.ofNullable(fareServiceClient.reportRideCompleted(event));
    }
}
