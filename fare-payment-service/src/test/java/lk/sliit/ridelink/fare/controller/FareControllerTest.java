package lk.sliit.ridelink.fare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.fare.config.InternalApiKeyFilter;
import lk.sliit.ridelink.fare.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.fare.config.JwtUtil;
import lk.sliit.ridelink.fare.config.SecurityConfig;
import lk.sliit.ridelink.fare.dto.FareBreakdown;
import lk.sliit.ridelink.fare.dto.FareEstimateRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lk.sliit.ridelink.fare.service.FareCalculator;
import lk.sliit.ridelink.fare.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({FareController.class, InternalFareController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class FareControllerTest {

    private static final String PASSENGER_TOKEN = "passenger.token";
    private static final String INTERNAL_KEY = "ridelink-internal-service-key-change-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FareCalculator fareCalculator;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtUtil jwtUtil;

    private final FareEstimateRequest validRequest = FareEstimateRequest.builder()
            .pickupLatitude(6.9344).pickupLongitude(79.8428)
            .dropoffLatitude(6.8905).dropoffLongitude(79.8565)
            .vehicleType(VehicleType.CAR)
            .build();

    @BeforeEach
    void setUp() {
        when(jwtUtil.isTokenValid(PASSENGER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(PASSENGER_TOKEN)).thenReturn("acc-psg-001");
        when(jwtUtil.extractRoles(PASSENGER_TOKEN)).thenReturn(List.of("PASSENGER"));

        when(fareCalculator.estimate(any(FareEstimateRequest.class))).thenReturn(FareBreakdown.builder()
                .distanceKm(5.14).durationMinutes(10.28).vehicleType(VehicleType.CAR)
                .baseFare(new BigDecimal("100.00")).distanceCharge(new BigDecimal("308.40"))
                .timeCharge(new BigDecimal("102.80")).vehicleMultiplier(1.0)
                .totalFare(new BigDecimal("511.20")).currency("LKR")
                .build());
    }

    @Test
    @DisplayName("POST /api/fares/estimate - a signed-in user gets the breakdown")
    void estimateWithToken() throws Exception {
        mockMvc.perform(post("/api/fares/estimate")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFare").value(511.20))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.distanceKm").value(5.14));
    }

    @Test
    @DisplayName("POST /api/fares/estimate - no token gives 401 JSON")
    void estimateWithoutToken() throws Exception {
        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid Bearer token"));
        verifyNoInteractions(fareCalculator);
    }

    @Test
    @DisplayName("POST /api/fares/estimate - missing and out-of-range coordinates give 400")
    void estimateValidation() throws Exception {
        FareEstimateRequest invalid = FareEstimateRequest.builder()
                .pickupLatitude(95.0).pickupLongitude(79.8)
                .dropoffLongitude(200.0)
                .build();

        mockMvc.perform(post("/api/fares/estimate")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Pickup latitude must be between -90 and 90")))
                .andExpect(jsonPath("$.message", containsString("Destination latitude is required")))
                .andExpect(jsonPath("$.message", containsString("Destination longitude must be between -180 and 180")));
        verifyNoInteractions(fareCalculator);
    }

    @Test
    @DisplayName("POST /api/fares/estimate - an unknown vehicle type gives 400")
    void estimateUnknownVehicle() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest).replace("\"CAR\"", "\"HELICOPTER\"");

        mockMvc.perform(post("/api/fares/estimate")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed or contains an invalid value"));
    }

    @Test
    @DisplayName("POST /api/fares/internal/estimate - Ride Management with the internal key")
    void internalEstimateWithKey() throws Exception {
        mockMvc.perform(post("/api/fares/internal/estimate")
                        .header(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFare").value(511.20));
    }

    @Test
    @DisplayName("POST /api/fares/internal/estimate - a user JWT is not enough (401)")
    void internalEstimateWithUserToken() throws Exception {
        mockMvc.perform(post("/api/fares/internal/estimate")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Api-Key header"));
        verifyNoInteractions(fareCalculator);
    }

    @Test
    @DisplayName("POST /api/payments/internal/ride-completed - records the completed ride")
    void rideCompletedWithKey() throws Exception {
        when(paymentService.recordCompletedRide(any(RideCompletedEvent.class))).thenReturn(PaymentResponse.builder()
                .rideId("ride-001").status(PaymentStatus.PENDING).totalAmount(new BigDecimal("900.00")).build());

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId("ride-001").passengerId("acc-psg-001")
                .pickupLatitude(6.9271).pickupLongitude(79.8612)
                .dropoffLatitude(6.9000).dropoffLongitude(79.8500)
                .build();

        mockMvc.perform(post("/api/payments/internal/ride-completed")
                        .header(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(900.00));
    }

    @Test
    @DisplayName("POST /api/payments/internal/ride-completed - an event without rideId/passengerId gives 400")
    void rideCompletedValidation() throws Exception {
        mockMvc.perform(post("/api/payments/internal/ride-completed")
                        .header(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupLatitude\": 6.9, \"pickupLongitude\": 79.8, " +
                                "\"dropoffLatitude\": 6.95, \"dropoffLongitude\": 79.85}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("rideId is required")))
                .andExpect(jsonPath("$.message", containsString("passengerId is required")));
        verifyNoInteractions(paymentService);
    }
}
