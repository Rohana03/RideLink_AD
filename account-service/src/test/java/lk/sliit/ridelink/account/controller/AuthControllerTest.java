package lk.sliit.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.account.config.InternalApiKeyFilter;
import lk.sliit.ridelink.account.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.account.config.JwtUtil;
import lk.sliit.ridelink.account.config.SecurityConfig;
import lk.sliit.ridelink.account.dto.AccountResponse;
import lk.sliit.ridelink.account.dto.AuthResponse;
import lk.sliit.ridelink.account.dto.LoginRequest;
import lk.sliit.ridelink.account.dto.RegisterRequest;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import lk.sliit.ridelink.account.exception.AccountNotActiveException;
import lk.sliit.ridelink.account.exception.DuplicateResourceException;
import lk.sliit.ridelink.account.exception.InvalidCredentialsException;
import lk.sliit.ridelink.account.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtUtil jwtUtil;

    private RegisterRequest validRegistration() {
        return RegisterRequest.builder()
                .fullName("Nimal Perera")
                .email("nimal@example.com")
                .password("Passw0rd123")
                .phoneNumber("+94771234567")
                .role(Role.DRIVER)
                .build();
    }

    private AccountResponse driverResponse() {
        return AccountResponse.builder()
                .id("acc-drv-001")
                .email("nimal@example.com")
                .fullName("Nimal Perera")
                .phoneNumber("+94771234567")
                .role(Role.DRIVER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register - public, returns 201 without any password field")
    void registerReturnsCreated() throws Exception {
        when(accountService.register(any(RegisterRequest.class))).thenReturn(driverResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("acc-drv-001"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid fields give 400 with every validation message")
    void registerValidationFailure() throws Exception {
        RegisterRequest invalid = RegisterRequest.builder()
                .fullName("")
                .email("not-an-email")
                .password("short")
                .phoneNumber("12ab")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.message", containsString("Full name is required")))
                .andExpect(jsonPath("$.message", containsString("Email must be a valid email address")))
                .andExpect(jsonPath("$.message", containsString("Password must be between 8 and 72 characters")))
                .andExpect(jsonPath("$.message", containsString("Phone number must be 9 to 15 digits")))
                .andExpect(jsonPath("$.message", containsString("Role is required")));

        verify(accountService, never()).register(any());
    }

    @Test
    @DisplayName("POST /api/auth/register - a password without a digit is rejected")
    void registerWeakPassword() throws Exception {
        RegisterRequest weak = validRegistration();
        weak.setPassword("onlyletters");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must contain at least one letter and one digit"));
    }

    @Test
    @DisplayName("POST /api/auth/register - an unknown role value gives 400, not 500")
    void registerUnknownRole() throws Exception {
        String body = objectMapper.writeValueAsString(validRegistration()).replace("\"DRIVER\"", "\"PILOT\"");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed or contains an invalid value"));
    }

    @Test
    @DisplayName("POST /api/auth/register - a duplicate email gives 409")
    void registerDuplicateEmail() throws Exception {
        when(accountService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Email is already registered: nimal@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/auth/login - public, returns the token and account")
    void loginReturnsToken() throws Exception {
        when(accountService.login(any(LoginRequest.class))).thenReturn(AuthResponse.builder()
                .accessToken("signed.jwt.token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .account(driverResponse())
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nimal@example.com", "Passw0rd123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.account.id").value("acc-drv-001"));
    }

    @Test
    @DisplayName("POST /api/auth/login - wrong credentials give 401 in the standard error shape")
    void loginWrongCredentials() throws Exception {
        when(accountService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nimal@example.com", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - a suspended account gives 403")
    void loginSuspended() throws Exception {
        when(accountService.login(any(LoginRequest.class)))
                .thenThrow(new AccountNotActiveException("Account is SUSPENDED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nimal@example.com", "Passw0rd123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is SUSPENDED"));
    }

    @Test
    @DisplayName("POST /api/auth/login - malformed JSON gives 400")
    void loginMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest());
    }
}
