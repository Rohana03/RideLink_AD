package lk.sliit.ridelink.fare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.sliit.ridelink.fare.config.InternalApiKeyFilter;
import lk.sliit.ridelink.fare.config.JwtAuthenticationFilter;
import lk.sliit.ridelink.fare.config.JwtUtil;
import lk.sliit.ridelink.fare.config.SecurityConfig;
import lk.sliit.ridelink.fare.dto.PaymentRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.dto.ReceiptResponse;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.exception.ForbiddenOperationException;
import lk.sliit.ridelink.fare.exception.InvalidPaymentStateException;
import lk.sliit.ridelink.fare.exception.PaymentDeclinedException;
import lk.sliit.ridelink.fare.exception.ResourceNotFoundException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PaymentController.class, ReceiptController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class PaymentControllerTest {

    private static final String PASSENGER_TOKEN = "passenger.token";
    private static final String DRIVER_TOKEN = "driver.token";
    private static final String ADMIN_TOKEN = "admin.token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtUtil jwtUtil;

    private PaymentResponse paid;

    private void token(String token, String userId, String role) {
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn(userId);
        when(jwtUtil.extractRoles(token)).thenReturn(List.of(role));
    }

    @BeforeEach
    void setUp() {
        token(PASSENGER_TOKEN, "acc-psg-001", "PASSENGER");
        token(DRIVER_TOKEN, "acc-drv-001", "DRIVER");
        token(ADMIN_TOKEN, "acc-admin-001", "ADMIN");

        paid = PaymentResponse.builder()
                .rideId("ride-001")
                .passengerId("acc-psg-001")
                .totalAmount(new BigDecimal("900.00"))
                .currency("LKR")
                .status(PaymentStatus.PAID)
                .method(PaymentMethod.CASH)
                .receiptNumber("RCP-20260924-ABC123")
                .build();
    }

    private String cash() throws Exception {
        return objectMapper.writeValueAsString(new PaymentRequest(PaymentMethod.CASH, null));
    }

    // ---- Paying ----

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - the passenger pays; identity comes from the token")
    void passengerPays() throws Exception {
        when(paymentService.pay(eq("ride-001"), eq("acc-psg-001"), any(PaymentRequest.class))).thenReturn(paid);

        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cash()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.receiptNumber").value("RCP-20260924-ABC123"));
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - a driver cannot pay (403)")
    void driverCannotPay() throws Exception {
        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .header("Authorization", "Bearer " + DRIVER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cash()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - no token gives 401")
    void payWithoutToken() throws Exception {
        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cash()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - a declined card gives 402 in the standard error shape")
    void declinedCard() throws Exception {
        when(paymentService.pay(eq("ride-001"), eq("acc-psg-001"), any(PaymentRequest.class)))
                .thenThrow(new PaymentDeclinedException("Card declined (simulated)"));

        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest(PaymentMethod.CARD, "4111111111110000"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error").value("Payment Required"))
                .andExpect(jsonPath("$.message").value("Card declined (simulated)"))
                .andExpect(jsonPath("$.path").value("/api/payments/ride-001/pay"));
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - paying twice gives 409")
    void payTwice() throws Exception {
        when(paymentService.pay(eq("ride-001"), eq("acc-psg-001"), any(PaymentRequest.class)))
                .thenThrow(new InvalidPaymentStateException("Ride ride-001 is already paid"));

        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cash()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - missing method and a malformed card number give 400")
    void payValidation() throws Exception {
        mockMvc.perform(post("/api/payments/ride-001/pay")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\": \"4111-abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Card number must be 12 to 19 digits; Payment method is required (CARD, CASH or WALLET)"));
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/payments/{rideId}/pay - an unknown ride gives 404")
    void payUnknownRide() throws Exception {
        when(paymentService.pay(eq("missing"), eq("acc-psg-001"), any(PaymentRequest.class)))
                .thenThrow(new ResourceNotFoundException("No completed ride found for payment with ride ID: missing"));

        mockMvc.perform(post("/api/payments/missing/pay")
                        .header("Authorization", "Bearer " + PASSENGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cash()))
                .andExpect(status().isNotFound());
    }

    // ---- Viewing ----

    @Test
    @DisplayName("GET /api/payments/{rideId} - the driver can view; admin flag is false")
    void driverViewsPayment() throws Exception {
        when(paymentService.getPayment("ride-001", "acc-drv-001", false)).thenReturn(paid);

        mockMvc.perform(get("/api/payments/ride-001").header("Authorization", "Bearer " + DRIVER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").value("ride-001"));
    }

    @Test
    @DisplayName("GET /api/payments/{rideId} - an unrelated user gets 403 with the service's message")
    void strangerCannotView() throws Exception {
        when(paymentService.getPayment("ride-001", "acc-drv-001", false))
                .thenThrow(new ForbiddenOperationException("You can only view payments for your own rides"));

        mockMvc.perform(get("/api/payments/ride-001").header("Authorization", "Bearer " + DRIVER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only view payments for your own rides"));
    }

    @Test
    @DisplayName("GET /api/payments/me - a passenger lists their own payments")
    void myPayments() throws Exception {
        when(paymentService.getPaymentsForPassenger("acc-psg-001")).thenReturn(List.of(paid));

        mockMvc.perform(get("/api/payments/me").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/payments - admin lists by status; the admin flag reaches the service")
    void adminListsPayments() throws Exception {
        when(paymentService.listPayments(PaymentStatus.PAID)).thenReturn(List.of(paid));

        mockMvc.perform(get("/api/payments").param("status", "PAID")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    @DisplayName("GET /api/payments - a passenger gets 403")
    void passengerCannotListAll() throws Exception {
        mockMvc.perform(get("/api/payments").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("GET /api/payments?status=REFUNDED - an unknown status gives 400")
    void unknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/payments").param("status", "REFUNDED")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'REFUNDED' for parameter 'status'"));
    }

    // ---- Receipts ----

    @Test
    @DisplayName("GET /api/receipts/{rideId} - returns the receipt; admin callers are flagged as admin")
    void adminGetsReceipt() throws Exception {
        when(paymentService.getReceipt("ride-001", "acc-admin-001", true)).thenReturn(ReceiptResponse.builder()
                .receiptNumber("RCP-20260924-ABC123").rideId("ride-001")
                .totalAmount(new BigDecimal("900.00")).fareRule(ReceiptResponse.FARE_RULE).build());

        mockMvc.perform(get("/api/receipts/ride-001").header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("RCP-20260924-ABC123"))
                .andExpect(jsonPath("$.fareRule").value(ReceiptResponse.FARE_RULE));
    }

    @Test
    @DisplayName("GET /api/receipts/{rideId} - before payment gives 409")
    void receiptBeforePayment() throws Exception {
        when(paymentService.getReceipt("ride-001", "acc-psg-001", false))
                .thenThrow(new InvalidPaymentStateException("Receipt is available only after payment is completed (current status: PENDING)"));

        mockMvc.perform(get("/api/receipts/ride-001").header("Authorization", "Bearer " + PASSENGER_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Receipt is available only after payment is completed (current status: PENDING)"));
    }
}
