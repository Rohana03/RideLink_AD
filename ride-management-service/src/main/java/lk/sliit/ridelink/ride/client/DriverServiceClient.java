package lk.sliit.ridelink.ride.client;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;

/**
 * Synchronous REST calls to the Driver &amp; Vehicle Service's internal API.
 * Sync is used because assignment needs the answer (who is available) before it can continue.
 */
@Component
public class DriverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceClient.class);
    private static final String UNAVAILABLE = "Driver & Vehicle Service is unavailable";

    private final RestClient restClient;

    public DriverServiceClient(@Qualifier("driverServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** AVAILABLE, ACTIVE drivers within radiusKm of the pickup, nearest first (Haversine, done by Driver & Vehicle). */
    public List<EligibleDriver> findEligibleDrivers(double pickupLatitude, double pickupLongitude,
                                                    VehicleType vehicleType, double radiusKm, int limit) {
        try {
            List<EligibleDriver> drivers = restClient.get()
                    .uri(uri -> {
                        uri.path("/api/drivers/internal/eligible")
                                .queryParam("pickupLatitude", pickupLatitude)
                                .queryParam("pickupLongitude", pickupLongitude)
                                .queryParam("radiusKm", radiusKm)
                                .queryParam("limit", limit);
                        if (vehicleType != null) {
                            uri.queryParam("vehicleType", vehicleType.name());
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EligibleDriver>>() { });
            return drivers != null ? drivers : List.of();
        } catch (RestClientException e) {
            throw new DownstreamServiceException(UNAVAILABLE + " (eligible driver search failed)", e);
        }
    }

    /**
     * Moves a driver AVAILABLE -> ON_TRIP.
     *
     * @return false if Driver &amp; Vehicle answers 409 (the driver was taken by another ride in the meantime)
     */
    public boolean markOnTrip(String driverId) {
        try {
            updateStatus(driverId, "ON_TRIP");
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                log.info("Driver {} is no longer available", driverId);
                return false;
            }
            throw new DownstreamServiceException(UNAVAILABLE + " (could not reserve driver " + driverId + ")", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException(UNAVAILABLE + " (could not reserve driver " + driverId + ")", e);
        }
    }

    /** Moves a driver back to AVAILABLE after the ride is completed or cancelled. */
    public void markAvailable(String driverId) {
        try {
            updateStatus(driverId, "AVAILABLE");
        } catch (RestClientException e) {
            throw new DownstreamServiceException(UNAVAILABLE + " (could not release driver " + driverId + ")", e);
        }
    }

    private void updateStatus(String driverId, String status) {
        restClient.patch()
                .uri("/api/drivers/internal/{id}/status", driverId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }
}
