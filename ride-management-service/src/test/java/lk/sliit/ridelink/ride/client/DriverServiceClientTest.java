package lk.sliit.ridelink.ride.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import lk.sliit.ridelink.ride.config.ServiceClientConfig;
import lk.sliit.ridelink.ride.entity.VehicleType;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;

/** Checks the exact requests sent to the Driver &amp; Vehicle Service's internal API. */
class DriverServiceClientTest {

    private MockRestServiceServer server;
    private DriverServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://driver-service")
                .defaultHeader(ServiceClientConfig.INTERNAL_API_KEY_HEADER, "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DriverServiceClient(builder.build());
    }

    @Test
    void eligibleDriverSearchSendsPickupRadiusLimitVehicleTypeAndApiKey() {
        server.expect(requestTo(startsWith("http://driver-service/api/drivers/internal/eligible")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("pickupLatitude", "6.9271"))
                .andExpect(queryParam("pickupLongitude", "79.8612"))
                .andExpect(queryParam("radiusKm", "5.0"))
                .andExpect(queryParam("limit", "5"))
                .andExpect(queryParam("vehicleType", "VAN"))
                .andExpect(header(ServiceClientConfig.INTERNAL_API_KEY_HEADER, "test-key"))
                .andRespond(withSuccess("""
                        [{"driverId":"driver-1","userId":"driver-user-1","fullName":"Nimal Perera",
                          "rating":4.9,"distanceKm":0.8,
                          "vehicle":{"vehicleType":"VAN","make":"Toyota","model":"HiAce","licensePlate":"WP PA-1234","color":"White"}}]
                        """, MediaType.APPLICATION_JSON));

        List<EligibleDriver> drivers = client.findEligibleDrivers(6.9271, 79.8612, VehicleType.VAN, 5.0, 5);

        assertThat(drivers).hasSize(1);
        assertThat(drivers.get(0).driverId()).isEqualTo("driver-1");
        assertThat(drivers.get(0).userId()).isEqualTo("driver-user-1");
        assertThat(drivers.get(0).vehicle().vehicleType()).isEqualTo("VAN");
        server.verify();
    }

    @Test
    void markOnTripPatchesTheInternalStatusEndpoint() {
        server.expect(requestTo("http://driver-service/api/drivers/internal/driver-1/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"status\":\"ON_TRIP\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.markOnTrip("driver-1")).isTrue();
        server.verify();
    }

    @Test
    void conflictMeansTheDriverWasTakenNotAnOutage() {
        server.expect(requestTo("http://driver-service/api/drivers/internal/driver-1/status"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThat(client.markOnTrip("driver-1")).isFalse();
    }

    @Test
    void markAvailableSendsAvailable() {
        server.expect(requestTo("http://driver-service/api/drivers/internal/driver-1/status"))
                .andExpect(content().json("{\"status\":\"AVAILABLE\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.markAvailable("driver-1");
        server.verify();
    }

    @Test
    void serverErrorsBecomeDownstreamServiceException() {
        server.expect(requestTo(startsWith("http://driver-service/api/drivers/internal/eligible")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.findEligibleDrivers(6.9, 79.8, null, 5.0, 5))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("Driver & Vehicle Service is unavailable");
    }

    @Test
    void anUnexpectedErrorWhileReservingIsAnOutageToo() {
        server.expect(requestTo("http://driver-service/api/drivers/internal/driver-1/status"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.markOnTrip("driver-1"))
                .isInstanceOf(DownstreamServiceException.class);
    }
}
