package lk.sliit.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.account.config.InternalApiKeyFilter;
import lk.sliit.ridelink.account.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.account.config.JwtUtil;
import lk.sliit.ridelink.account.config.SecurityConfig;
import lk.sliit.ridelink.account.dto.*;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.exception.BadRequestException;
import lk.sliit.ridelink.account.exception.ResourceNotFoundException;
import lk.sliit.ridelink.account.service.AccountService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AccountController.class, InternalAccountController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class AccountControllerTest {

    private static final String PASSENGER_TOKEN = "passenger.token";
    private static final String ADMIN_TOKEN = "admin.token";
    private static final String INTERNAL_KEY = "ridelink-internal-service-key-change-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtUtil jwtUtil;

    private AccountResponse passengerResponse;

    @BeforeEach
    void setUp() {
        when(jwtUtil.isTokenValid(PASSENGER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(PASSENGER_TOKEN)).thenReturn("acc-psg-001");
        when(jwtUtil.extractRoles(PASSENGER_TOKEN)).thenReturn(List.of("PASSENGER"));

        when(jwtUtil.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(ADMIN_TOKEN)).thenReturn("acc-admin-001");
        when(jwtUtil.extractRoles(ADMIN_TOKEN)).thenReturn(List.of("ADMIN"));

        passengerResponse = AccountResponse.builder()
                .id("acc-psg-001")
                .email("amaya@example.com")
                .fullName("Amaya Silva")
                .phoneNumber("+94771112233")
                .role(Role.PASSENGER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    // ---- Own account ----

    @Test
    @DisplayName("GET /api/accounts/me - returns the caller's account, identified by the token")
    void getMyAccount() throws Exception {
        when(accountService.getMyAccount("acc-psg-001")).thenReturn(passengerResponse);

        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acc-psg-001"))
                .andExpect(jsonPath("$.email").value("amaya@example.com"));
    }

    @Test
    @DisplayName("GET /api/accounts/me - no token gives 401 JSON")
    void getMyAccountWithoutToken() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid Bearer token"))
                .andExpect(jsonPath("$.path").value("/api/accounts/me"));
    }

    @Test
    @DisplayName("GET /api/accounts/me - an invalid token gives 401")
    void getMyAccountWithInvalidToken() throws Exception {
        when(jwtUtil.isTokenValid("forged.token")).thenReturn(false);

        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer forged.token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("PUT /api/accounts/me - updates the caller's profile")
    void updateMyProfile() throws Exception {
        passengerResponse.setFullName("Amaya S. Silva");
        when(accountService.updateMyProfile(eq("acc-psg-001"), any(ProfileUpdateRequest.class)))
                .thenReturn(passengerResponse);

        mockMvc.perform(put("/api/accounts/me")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProfileUpdateRequest("Amaya S. Silva", "+94771112233"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Amaya S. Silva"));
    }

    @Test
    @DisplayName("PUT /api/accounts/me - an invalid phone number gives 400")
    void updateMyProfileInvalidPhone() throws Exception {
        mockMvc.perform(put("/api/accounts/me")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProfileUpdateRequest("Amaya", "call-me"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone number must be 9 to 15 digits, optionally starting with +"));
    }

    @Test
    @DisplayName("PUT /api/accounts/me/password - returns 204 on success")
    void changePassword() throws Exception {
        mockMvc.perform(put("/api/accounts/me/password")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("Passw0rd123", "NewPassw0rd"))))
                .andExpect(status().isNoContent());

        verify(accountService).changePassword(eq("acc-psg-001"), any(PasswordChangeRequest.class));
    }

    @Test
    @DisplayName("PUT /api/accounts/me/password - a wrong current password gives 400")
    void changePasswordWrongCurrent() throws Exception {
        doThrow(new BadRequestException("Current password is incorrect"))
                .when(accountService).changePassword(eq("acc-psg-001"), any(PasswordChangeRequest.class));

        mockMvc.perform(put("/api/accounts/me/password")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("WrongPass1", "NewPassw0rd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    // ---- Administration ----

    @Test
    @DisplayName("GET /api/accounts - a passenger gets 403 JSON")
    void passengerCannotListAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/accounts?role=PASSENGER - an admin can list with filters")
    void adminListsAccounts() throws Exception {
        when(accountService.listAccounts(Role.PASSENGER, null)).thenReturn(List.of(passengerResponse));

        mockMvc.perform(get("/api/accounts").param("role", "PASSENGER")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("PASSENGER"));
    }

    @Test
    @DisplayName("GET /api/accounts?role=PILOT - an unknown filter value gives 400")
    void adminListInvalidFilter() throws Exception {
        mockMvc.perform(get("/api/accounts").param("role", "PILOT")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'PILOT' for parameter 'role'"));
    }

    @Test
    @DisplayName("GET /api/accounts/{id} - an unknown ID gives 404")
    void adminGetMissingAccount() throws Exception {
        when(accountService.getAccountById("missing"))
                .thenThrow(new ResourceNotFoundException("Account not found with ID: missing"));

        mockMvc.perform(get("/api/accounts/missing").header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found with ID: missing"));
    }

    @Test
    @DisplayName("PATCH /api/accounts/{id}/status - an admin suspends an account")
    void adminSuspendsAccount() throws Exception {
        passengerResponse.setStatus(AccountStatus.SUSPENDED);
        when(accountService.updateStatus("acc-admin-001", "acc-psg-001", AccountStatus.SUSPENDED))
                .thenReturn(passengerResponse);

        mockMvc.perform(patch("/api/accounts/acc-psg-001/status")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StatusUpdateRequest(AccountStatus.SUSPENDED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("PATCH /api/accounts/{id}/status - a missing status gives 400")
    void adminStatusMissingValue() throws Exception {
        mockMvc.perform(patch("/api/accounts/acc-psg-001/status")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status is required (ACTIVE, SUSPENDED or DEACTIVATED)"));
    }

    @Test
    @DisplayName("PATCH /api/accounts/{id}/status - a passenger gets 403")
    void passengerCannotChangeStatus() throws Exception {
        mockMvc.perform(patch("/api/accounts/acc-psg-001/status")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StatusUpdateRequest(AccountStatus.ACTIVE))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("PATCH /api/accounts/{id}/role - an admin changes a role")
    void adminChangesRole() throws Exception {
        passengerResponse.setRole(Role.DRIVER);
        when(accountService.updateRole("acc-admin-001", "acc-psg-001", Role.DRIVER)).thenReturn(passengerResponse);

        mockMvc.perform(patch("/api/accounts/acc-psg-001/role")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleUpdateRequest(Role.DRIVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    // ---- Internal (service-to-service) ----

    @Test
    @DisplayName("GET /api/accounts/internal/{id} - works with the internal API key and no JWT")
    void internalLookupWithKey() throws Exception {
        when(accountService.getAccountById("acc-psg-001")).thenReturn(passengerResponse);

        mockMvc.perform(get("/api/accounts/internal/acc-psg-001")
                        .header(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/accounts/internal/{id} - a wrong key gives 401")
    void internalLookupWrongKey() throws Exception {
        mockMvc.perform(get("/api/accounts/internal/acc-psg-001")
                        .header(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Api-Key header"));
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/accounts/internal/{id} - a user JWT alone is not enough")
    void internalLookupWithUserToken() throws Exception {
        mockMvc.perform(get("/api/accounts/internal/acc-psg-001")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }
}
