package lk.sliit.ridelink.ride.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import lk.sliit.ridelink.ride.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.ride.config.JwtUtil;
import lk.sliit.ridelink.ride.config.SecurityConfig;
import lk.sliit.ridelink.ride.dto.CreateRideRequest;
import lk.sliit.ridelink.ride.dto.LocationDto;
import lk.sliit.ridelink.ride.dto.RideResponse;
import lk.sliit.ridelink.ride.entity.RideStatus;
import lk.sliit.ridelink.ride.exception.DownstreamServiceException;
import lk.sliit.ridelink.ride.exception.InvalidRideStatusTransitionException;
import lk.sliit.ridelink.ride.exception.NoDriverAvailableException;
import lk.sliit.ridelink.ride.service.RideCaller;
import lk.sliit.ridelink.ride.service.RideService;

@WebMvcTest(RideController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RideControllerTest {

    private static final String PASSENGER_TOKEN = "passenger.token";
    private static final String DRIVER_TOKEN = "driver.token";
    private static final String ADMIN_TOKEN = "admin.token";
    private static final RideCaller PASSENGER = new RideCaller("passenger-1", false);
    private static final RideCaller DRIVER = new RideCaller("driver-user-1", false);

    private static final String CREATE_BODY = """
            {"pickupLocation":{"label":"Colombo Fort","latitude":6.9344,"longitude":79.8428},
             "destinationLocation":{"label":"Bambalapitiya","latitude":6.8905,"longitude":79.8565}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RideService rideService;

    @MockBean
    private JwtUtil jwtUtil;

    private void token(String token, String userId, String role) {
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn(userId);
        when(jwtUtil.extractRoles(token)).thenReturn(List.of(role));
    }

    @BeforeEach
    void setUp() {
        token(PASSENGER_TOKEN, "passenger-1", "PASSENGER");
        token(DRIVER_TOKEN, "driver-user-1", "DRIVER");
        token(ADMIN_TOKEN, "admin-1", "ADMIN");
    }

    private static RideResponse ride(RideStatus status) {
        return new RideResponse("ride-1", "passenger-1", null, null, null, null,
                new LocationDto("Colombo Fort", 6.9344, 79.8428), new LocationDto("Bambalapitiya", 6.8905, 79.8565),
                status, 5.11, 508.80, null, "LKR", null, null, Instant.now(), null, null, null, null, null);
    }

    @Test
    void requestingARideWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/rides").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid Bearer token"))
                .andExpect(jsonPath("$.path").value("/api/rides"));
        verifyNoInteractions(rideService);
    }

    @Test
    void aPassengerRequestsARideAsThemselves() throws Exception {
        when(rideService.createRide(eq("passenger-1"), any(CreateRideRequest.class))).thenReturn(ride(RideStatus.REQUESTED));

        mockMvc.perform(post("/api/rides").header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.estimatedFare").value(508.80));
    }

    @Test
    void aDriverCannotRequestARide() throws Exception {
        mockMvc.perform(post("/api/rides").header("Authorization", "Bearer " + DRIVER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
        verifyNoInteractions(rideService);
    }

    @Test
    void missingLocationsGiveValidationErrors() throws Exception {
        mockMvc.perform(post("/api/rides").header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pickupLocation: pickupLocation is required")))
                .andExpect(jsonPath("$.message", containsString("destinationLocation: destinationLocation is required")));
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/api/rides").header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed or contains an invalid value"));
    }

    @Test
    void fareServiceDownGivesServiceUnavailable() throws Exception {
        when(rideService.createRide(eq("passenger-1"), any(CreateRideRequest.class)))
                .thenThrow(new DownstreamServiceException("Fare & Payment Service is unavailable (fare estimate failed)", null));

        mockMvc.perform(post("/api/rides").header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Fare & Payment Service is unavailable (fare estimate failed)"));
    }

    @Test
    void noAvailableDriverGivesConflict() throws Exception {
        when(rideService.assignDriver("ride-1", PASSENGER)).thenThrow(new NoDriverAvailableException(5.0));

        mockMvc.perform(patch("/api/rides/ride-1/assign").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No available driver within 5.0 km of the pickup location"));
    }

    @Test
    void aDriverCannotAssignADriver() throws Exception {
        mockMvc.perform(patch("/api/rides/ride-1/assign").header("Authorization", "Bearer " + DRIVER_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDriverAcceptsTheRide() throws Exception {
        when(rideService.acceptRide("ride-1", DRIVER)).thenReturn(ride(RideStatus.ACCEPTED));

        mockMvc.perform(patch("/api/rides/ride-1/accept").header("Authorization", "Bearer " + DRIVER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void aPassengerCannotCompleteARide() throws Exception {
        mockMvc.perform(patch("/api/rides/ride-1/complete").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rideService);
    }

    @Test
    void anInvalidStatusChangeGivesConflict() throws Exception {
        when(rideService.startRide("ride-1", DRIVER))
                .thenThrow(new InvalidRideStatusTransitionException(RideStatus.COMPLETED, RideStatus.IN_PROGRESS));

        mockMvc.perform(patch("/api/rides/ride-1/start").header("Authorization", "Bearer " + DRIVER_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot transition ride from COMPLETED to IN_PROGRESS"));
    }

    @Test
    void negativeMeasuredDistanceIsRejected() throws Exception {
        mockMvc.perform(patch("/api/rides/ride-1/complete").header("Authorization", "Bearer " + DRIVER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualDistanceKm\": -3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("actualDistanceKm: actualDistanceKm must not be negative"));
    }

    @Test
    void onlyAdminsListEveryRide() throws Exception {
        when(rideService.getRides(null, null, RideStatus.COMPLETED)).thenReturn(List.of(ride(RideStatus.COMPLETED)));

        mockMvc.perform(get("/api/rides").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rides").param("status", "COMPLETED").header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void myRidesUsesTheCallerFromTheToken() throws Exception {
        when(rideService.getMyRides(PASSENGER)).thenReturn(List.of(ride(RideStatus.REQUESTED)));

        mockMvc.perform(get("/api/rides/me").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
