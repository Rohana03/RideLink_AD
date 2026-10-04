package lk.sliit.ridelink.ride.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import lk.sliit.ridelink.ride.entity.Location;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.messaging.RideCompletedEvent;

/** Checks the exact requests sent to the Fare &amp; Payment Service's internal API. */
class FareServiceClientTest {

    private MockRestServiceServer server;
    private FareServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fare-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FareServiceClient(builder.build());
    }

    @Test
    void estimatePostsPickupDestinationAndVehicleType() {
        server.expect(requestTo("http://fare-service/api/fares/internal/estimate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"pickupLatitude":6.9344,"pickupLongitude":79.8428,
                         "dropoffLatitude":6.8905,"dropoffLongitude":79.8565,"vehicleType":"TUK_TUK"}
                        """))
                .andRespond(withSuccess("""
                        {"distanceKm":5.11,"durationMinutes":10.22,"totalFare":407.04,"currency":"LKR",
                         "baseFare":100.00,"vehicleMultiplier":0.8}
                        """, MediaType.APPLICATION_JSON));

        FareEstimate estimate = client.estimate(new Location("Fort", 6.9344, 79.8428),
                new Location("Bambalapitiya", 6.8905, 79.8565), VehicleType.TUK_TUK);

        assertThat(estimate.totalFare()).isEqualByComparingTo(new BigDecimal("407.04"));
        assertThat(estimate.distanceKm()).isEqualTo(5.11);
        assertThat(estimate.currency()).isEqualTo("LKR");
        server.verify();
    }

    @Test
    void completedRideIsPostedWithTheSharedContract() {
        server.expect(requestTo("http://fare-service/api/payments/internal/ride-completed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.rideId").value("ride-1"))
                .andExpect(jsonPath("$.passengerId").value("passenger-1"))
                .andExpect(jsonPath("$.driverUserId").value("driver-user-1"))
                .andExpect(jsonPath("$.vehicleType").value("VAN"))
                .andExpect(jsonPath("$.actualDistanceKm").value(10.0))
                .andRespond(withSuccess("""
                        {"rideId":"ride-1","totalAmount":1350.00,"currency":"LKR","status":"PENDING","attempts":0}
                        """, MediaType.APPLICATION_JSON));

        FarePayment payment = client.reportRideCompleted(new RideCompletedEvent("ride-1", "passenger-1", "driver-1",
                "driver-user-1", VehicleType.VAN, 6.9271, 79.8612, 6.9, 79.85, 10.0, 20.0, LocalDateTime.now()));

        assertThat(payment.totalAmount()).isEqualByComparingTo(new BigDecimal("1350.00"));
        assertThat(payment.status()).isEqualTo("PENDING");
        server.verify();
    }

    @Test
    void estimateFailureBecomesDownstreamServiceException() {
        server.expect(requestTo("http://fare-service/api/fares/internal/estimate")).andRespond(withServerError());

        assertThatThrownBy(() -> client.estimate(new Location("A", 6.9, 79.8), new Location("B", 6.95, 79.85), null))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("Fare & Payment Service is unavailable");
    }
}
