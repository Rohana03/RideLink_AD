package lk.sliit.ridelink.ride.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lk.sliit.ridelink.ride.entity.Location;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.messaging.RideCompletedEvent;

/** Synchronous REST calls to the Fare &amp; Payment Service's internal API. */
@Component
public class FareServiceClient {

    private static final String UNAVAILABLE = "Fare & Payment Service is unavailable";

    private final RestClient restClient;

    public FareServiceClient(@Qualifier("fareServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Estimate shown to the passenger when the ride is requested, so it must be synchronous. */
    public FareEstimate estimate(Location pickup, Location destination, VehicleType vehicleType) {
        Map<String, Object> body = new HashMap<>();
        body.put("pickupLatitude", pickup.getLatitude());
        body.put("pickupLongitude", pickup.getLongitude());
        body.put("dropoffLatitude", destination.getLatitude());
        body.put("dropoffLongitude", destination.getLongitude());
        if (vehicleType != null) {
            body.put("vehicleType", vehicleType.name());
        }
        try {
            FareEstimate estimate = restClient.post()
                    .uri("/api/fares/internal/estimate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(FareEstimate.class);
            if (estimate == null) {
                throw new DownstreamServiceException(UNAVAILABLE + " (empty fare estimate)", null);
            }
            return estimate;
        } catch (RestClientException e) {
            throw new DownstreamServiceException(UNAVAILABLE + " (fare estimate failed)", e);
        }
    }

    /** REST form of the ride-completed handoff; Fare & Payment calculates the final fare and opens a payment. */
    public FarePayment reportRideCompleted(RideCompletedEvent event) {
        try {
            return restClient.post()
                    .uri("/api/payments/internal/ride-completed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .body(FarePayment.class);
        } catch (RestClientException e) {
            throw new DownstreamServiceException(UNAVAILABLE + " (could not record completed ride " + event.rideId() + ")", e);
        }
    }
}
