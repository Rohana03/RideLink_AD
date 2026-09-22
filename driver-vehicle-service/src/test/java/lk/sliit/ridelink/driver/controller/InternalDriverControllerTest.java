package lk.sliit.ridelink.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.driver.config.InternalApiKeyFilter;
import lk.sliit.ridelink.driver.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.driver.config.JwtUtil;
import lk.sliit.ridelink.driver.config.SecurityConfig;
import lk.sliit.ridelink.driver.dto.DriverResponse;
import lk.sliit.ridelink.driver.dto.EligibleDriverResponse;
import lk.sliit.ridelink.driver.dto.InternalStatusUpdateRequest;
import lk.sliit.ridelink.driver.dto.VehicleResponse;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.VehicleType;
import lk.sliit.ridelink.driver.service.DriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalDriverController.class)
@Import({SecurityConfig.class, InternalApiKeyFilter.class, JwtAuthenticationFilter.class})
class InternalDriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DriverService driverService;

    @MockBean
    private JwtUtil jwtUtil;

    private static final String VALID_API_KEY = "ridelink-internal-service-key-change-me";

    private EligibleDriverResponse eligibleDriver;

    @BeforeEach
    void setUp() {
        eligibleDriver = EligibleDriverResponse.builder()
                .driverId("driver-doc-101")
                .userId("drv-101")
                .fullName("Sunil Silva")
                .phoneNumber("+94771234567")
                .rating(4.9)
                .totalTrips(30)
                .currentLatitude(6.9271)
                .currentLongitude(79.8612)
                .distanceKm(1.25)
                .serviceArea("Colombo")
                .vehicle(VehicleResponse.builder()
                        .make("Toyota")
                        .model("Axio")
                        .year(2019)
                        .licensePlate("WP CAZ-9988")
                        .vehicleType(VehicleType.CAR)
                        .seatingCapacity(4)
                        .build())
                .build();
    }

    @Test
    @DisplayName("GET /api/drivers/internal/eligible with valid API key returns 200 and drivers list")
    void shouldReturnEligibleDriversWhenApiKeyValid() throws Exception {
        when(driverService.findEligibleDrivers(eq(6.9344), eq(79.8428), eq(VehicleType.CAR), eq(5.0), eq(5)))
                .thenReturn(List.of(eligibleDriver));

        mockMvc.perform(get("/api/drivers/internal/eligible")
                        .header("X-Internal-Api-Key", VALID_API_KEY)
                        .param("pickupLatitude", "6.9344")
                        .param("pickupLongitude", "79.8428")
                        .param("vehicleType", "CAR")
                        .param("radiusKm", "5.0")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("driver-doc-101"))
                .andExpect(jsonPath("$[0].fullName").value("Sunil Silva"))
                .andExpect(jsonPath("$[0].distanceKm").value(1.25))
                .andExpect(jsonPath("$[0].vehicle.licensePlate").value("WP CAZ-9988"));
    }

    @Test
    @DisplayName("GET /api/drivers/internal/eligible without API key returns 401 Unauthorized")
    void shouldRejectWhenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/api/drivers/internal/eligible")
                        .param("pickupLatitude", "6.9344")
                        .param("pickupLongitude", "79.8428"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Api-Key header"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/internal/{id}/status updates driver status (e.g. ON_TRIP)")
    void shouldUpdateStatusInternally() throws Exception {
        InternalStatusUpdateRequest request = InternalStatusUpdateRequest.builder()
                .status(DriverAvailabilityStatus.ON_TRIP)
                .build();

        DriverResponse response = DriverResponse.builder()
                .id("driver-doc-101")
                .availabilityStatus(DriverAvailabilityStatus.ON_TRIP)
                .build();

        when(driverService.updateInternalStatus(eq("driver-doc-101"), eq(DriverAvailabilityStatus.ON_TRIP)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/drivers/internal/driver-doc-101/status")
                        .header("X-Internal-Api-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("ON_TRIP"));
    }
}
