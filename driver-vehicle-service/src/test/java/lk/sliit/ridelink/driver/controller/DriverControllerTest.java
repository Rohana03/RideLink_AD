package lk.sliit.ridelink.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.driver.config.InternalApiKeyFilter;
import lk.sliit.ridelink.driver.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.driver.config.JwtUtil;
import lk.sliit.ridelink.driver.config.SecurityConfig;
import lk.sliit.ridelink.driver.dto.*;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.OperationalStatus;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DriverController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DriverService driverService;

    @MockBean
    private JwtUtil jwtUtil;

    private String validJwtToken;
    private DriverResponse mockDriverResponse;

    @BeforeEach
    void setUp() {
        validJwtToken = "mock.valid.token";
        when(jwtUtil.isTokenValid(validJwtToken)).thenReturn(true);
        when(jwtUtil.extractUserId(validJwtToken)).thenReturn("user-drv-101");
        when(jwtUtil.extractRoles(validJwtToken)).thenReturn(List.of("DRIVER"));

        VehicleResponse vResp = VehicleResponse.builder()
                .id(1L)
                .make("Toyota")
                .model("Aqua")
                .year(2018)
                .color("White")
                .licensePlate("WP CAX-1234")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        mockDriverResponse = DriverResponse.builder()
                .id(1L)
                .userId("user-drv-101")
                .fullName("Kasun Bandara")
                .email("kasun@example.com")
                .phoneNumber("+94771234567")
                .licenseNumber("DL-123456")
                .serviceArea("Colombo")
                .serviceRadiusKm(15.0)
                .currentLatitude(6.9271)
                .currentLongitude(79.8612)
                .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
                .operationalStatus(OperationalStatus.ACTIVE)
                .rating(5.0)
                .totalTrips(0)
                .vehicle(vResp)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/drivers - Register driver returns 201 Created")
    void shouldRegisterDriver() throws Exception {
        VehicleRequest vReq = VehicleRequest.builder()
                .make("Toyota")
                .model("Aqua")
                .year(2018)
                .color("White")
                .licensePlate("WP CAX-1234")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        DriverRegistrationRequest request = DriverRegistrationRequest.builder()
                .fullName("Kasun Bandara")
                .email("kasun@example.com")
                .phoneNumber("+94771234567")
                .licenseNumber("DL-123456")
                .serviceArea("Colombo")
                .serviceRadiusKm(15.0)
                .vehicle(vReq)
                .build();

        when(driverService.registerDriver(eq("user-drv-101"), any(DriverRegistrationRequest.class)))
                .thenReturn(mockDriverResponse);

        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Kasun Bandara"))
                .andExpect(jsonPath("$.vehicle.licensePlate").value("WP CAX-1234"));
    }

    @Test
    @DisplayName("GET /api/drivers/me - Fetches current driver profile")
    void shouldGetCurrentDriverProfile() throws Exception {
        when(driverService.getDriverByUserId("user-drv-101")).thenReturn(mockDriverResponse);

        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-drv-101"))
                .andExpect(jsonPath("$.fullName").value("Kasun Bandara"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/me/availability - Toggles availability")
    void shouldUpdateAvailability() throws Exception {
        AvailabilityUpdateRequest req = AvailabilityUpdateRequest.builder()
                .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
                .build();

        when(driverService.updateAvailability("user-drv-101", DriverAvailabilityStatus.AVAILABLE))
                .thenReturn(mockDriverResponse);

        mockMvc.perform(patch("/api/drivers/me/availability")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"));
    }

    @Test
    @DisplayName("PUT /api/drivers/me/location - Updates GPS coordinates")
    void shouldUpdateLocation() throws Exception {
        LocationUpdateRequest req = LocationUpdateRequest.builder()
                .latitude(6.9271)
                .longitude(79.8612)
                .build();

        when(driverService.updateLocation("user-drv-101", 6.9271, 79.8612))
                .thenReturn(mockDriverResponse);

        mockMvc.perform(put("/api/drivers/me/location")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLatitude").value(6.9271))
                .andExpect(jsonPath("$.currentLongitude").value(79.8612));
    }

    @Test
    @DisplayName("GET /api/drivers/me without token should return 401/403")
    void shouldDenyAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/drivers/me"))
                .andExpect(status().isForbidden());
    }
}
