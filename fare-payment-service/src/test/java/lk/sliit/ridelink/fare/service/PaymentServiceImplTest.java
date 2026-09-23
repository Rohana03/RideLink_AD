package lk.sliit.ridelink.fare.service;

import lk.sliit.ridelink.fare.dto.PaymentRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.dto.ReceiptResponse;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.entity.Payment;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lk.sliit.ridelink.fare.exception.*;
import lk.sliit.ridelink.fare.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String RIDE_ID = "ride-001";
    private static final String PASSENGER_ID = "acc-psg-001";
    private static final String DRIVER_USER_ID = "acc-drv-001";

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentServiceImpl paymentService;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository,
                new FareCalculator(FareCalculatorTest.defaultProperties()));
        pendingPayment = Payment.builder()
                .id("pay-001")
                .rideId(RIDE_ID)
                .passengerId(PASSENGER_ID)
                .driverId("driver-doc-001")
                .driverUserId(DRIVER_USER_ID)
                .vehicleType(VehicleType.CAR)
                .distanceKm(10.0)
                .durationMinutes(20.0)
                .totalAmount(new BigDecimal("900.00"))
                .currency("LKR")
                .status(PaymentStatus.PENDING)
                .build();
    }

    private void saveReturnsArgument() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void rideHasPendingPayment() {
        when(paymentRepository.findByRideId(RIDE_ID)).thenReturn(Optional.of(pendingPayment));
    }

    @Nested
    @DisplayName("Ride completed -> final fare")
    class RecordCompletedRide {

        private RideCompletedEvent event() {
            return RideCompletedEvent.builder()
                    .rideId(RIDE_ID)
                    .passengerId(PASSENGER_ID)
                    .driverId("driver-doc-001")
                    .driverUserId(DRIVER_USER_ID)
                    .vehicleType(VehicleType.VAN)
                    .pickupLatitude(6.9271).pickupLongitude(79.8612)
                    .dropoffLatitude(6.9000).dropoffLongitude(79.8500)
                    .actualDistanceKm(10.0)
                    .actualDurationMinutes(20.0)
                    .build();
        }

        @Test
        @DisplayName("Opens a PENDING payment with the final fare from measured distance and time")
        void createsPendingPayment() {
            when(paymentRepository.findByRideId(RIDE_ID)).thenReturn(Optional.empty());
            saveReturnsArgument();

            PaymentResponse response = paymentService.recordCompletedRide(event());

            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(saved.capture());
            Payment payment = saved.getValue();
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertEquals(new BigDecimal("1350.00"), payment.getTotalAmount()); // 900 x 1.5 (VAN)
            assertEquals(DRIVER_USER_ID, payment.getDriverUserId());
            assertNotNull(payment.getRideCompletedAt());
            assertEquals(RIDE_ID, response.getRideId());
        }

        @Test
        @DisplayName("A repeated event for the same ride returns the existing payment and saves nothing")
        void duplicateEventIsIgnored() {
            pendingPayment.setStatus(PaymentStatus.PAID);
            rideHasPendingPayment();

            PaymentResponse response = paymentService.recordCompletedRide(event());

            assertEquals(PaymentStatus.PAID, response.getStatus());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Two simultaneous events: the unique index rejects one and the existing payment is returned")
        void concurrentDuplicateIsResolved() {
            when(paymentRepository.findByRideId(RIDE_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenThrow(new DuplicateKeyException("rideId"));

            PaymentResponse response = paymentService.recordCompletedRide(event());

            assertEquals("pay-001", response.getId());
        }
    }

    @Nested
    @DisplayName("Simulated payment")
    class Pay {

        @Test
        @DisplayName("CASH succeeds: PAID with a transaction reference and receipt number")
        void cashPaymentSucceeds() {
            rideHasPendingPayment();
            saveReturnsArgument();

            PaymentResponse response = paymentService.pay(RIDE_ID, PASSENGER_ID,
                    new PaymentRequest(PaymentMethod.CASH, null));

            assertEquals(PaymentStatus.PAID, response.getStatus());
            assertEquals(1, response.getAttempts());
            assertTrue(response.getTransactionReference().matches("TXN-[0-9A-F]{10}"));
            assertTrue(response.getReceiptNumber().matches("RCP-\\d{8}-[0-9A-F]{6}"));
            assertNotNull(response.getPaidAt());
            assertNull(response.getCardLast4());
        }

        @Test
        @DisplayName("CARD succeeds and only the last four digits are kept")
        void cardPaymentStoresOnlyLast4() {
            rideHasPendingPayment();
            saveReturnsArgument();

            PaymentResponse response = paymentService.pay(RIDE_ID, PASSENGER_ID,
                    new PaymentRequest(PaymentMethod.CARD, "4111111111111111"));

            assertEquals(PaymentStatus.PAID, response.getStatus());
            assertEquals("1111", response.getCardLast4());
        }

        @Test
        @DisplayName("A card ending in 0000 is declined: FAILED is saved, then a retry with CASH succeeds")
        void declinedCardThenRetry() {
            rideHasPendingPayment();
            saveReturnsArgument();

            PaymentDeclinedException ex = assertThrows(PaymentDeclinedException.class, () ->
                    paymentService.pay(RIDE_ID, PASSENGER_ID, new PaymentRequest(PaymentMethod.CARD, "4111111111110000")));

            assertTrue(ex.getMessage().contains("declined"));
            assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
            assertEquals(1, pendingPayment.getAttempts());
            assertNull(pendingPayment.getReceiptNumber());
            verify(paymentRepository).save(pendingPayment);

            PaymentResponse retry = paymentService.pay(RIDE_ID, PASSENGER_ID, new PaymentRequest(PaymentMethod.CASH, null));

            assertEquals(PaymentStatus.PAID, retry.getStatus());
            assertEquals(2, retry.getAttempts());
            assertNull(retry.getFailureReason());
            assertNull(retry.getCardLast4());
        }

        @Test
        @DisplayName("A PAID ride cannot be paid again (409)")
        void alreadyPaid() {
            pendingPayment.setStatus(PaymentStatus.PAID);
            pendingPayment.setReceiptNumber("RCP-20260924-ABC123");
            rideHasPendingPayment();

            InvalidPaymentStateException ex = assertThrows(InvalidPaymentStateException.class, () ->
                    paymentService.pay(RIDE_ID, PASSENGER_ID, new PaymentRequest(PaymentMethod.CASH, null)));

            assertTrue(ex.getMessage().contains("RCP-20260924-ABC123"));
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Another passenger cannot pay for this ride (403)")
        void otherPassengerForbidden() {
            rideHasPendingPayment();

            assertThrows(ForbiddenOperationException.class, () ->
                    paymentService.pay(RIDE_ID, "acc-psg-999", new PaymentRequest(PaymentMethod.CASH, null)));
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("CARD without a card number is rejected (400)")
        void cardNumberRequired() {
            rideHasPendingPayment();

            assertThrows(BadRequestException.class, () ->
                    paymentService.pay(RIDE_ID, PASSENGER_ID, new PaymentRequest(PaymentMethod.CARD, null)));
        }

        @Test
        @DisplayName("Paying for an unknown ride gives 404")
        void unknownRide() {
            when(paymentRepository.findByRideId("missing")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentService.pay("missing", PASSENGER_ID, new PaymentRequest(PaymentMethod.CASH, null)));
        }
    }

    @Nested
    @DisplayName("Viewing payments and receipts")
    class Viewing {

        @Test
        @DisplayName("The passenger, the ride's driver and an admin can view the payment")
        void involvedPartiesCanView() {
            rideHasPendingPayment();

            assertEquals(RIDE_ID, paymentService.getPayment(RIDE_ID, PASSENGER_ID, false).getRideId());
            assertEquals(RIDE_ID, paymentService.getPayment(RIDE_ID, DRIVER_USER_ID, false).getRideId());
            assertEquals(RIDE_ID, paymentService.getPayment(RIDE_ID, "acc-admin", true).getRideId());
        }

        @Test
        @DisplayName("An unrelated user cannot view the payment (403)")
        void strangerCannotView() {
            rideHasPendingPayment();

            assertThrows(ForbiddenOperationException.class,
                    () -> paymentService.getPayment(RIDE_ID, "acc-psg-999", false));
        }

        @Test
        @DisplayName("No receipt until the ride is PAID (409)")
        void receiptBeforePayment() {
            rideHasPendingPayment();

            InvalidPaymentStateException ex = assertThrows(InvalidPaymentStateException.class,
                    () -> paymentService.getReceipt(RIDE_ID, PASSENGER_ID, false));
            assertTrue(ex.getMessage().contains("PENDING"));
        }

        @Test
        @DisplayName("A PAID ride has a receipt with the fare breakdown and the fare rule")
        void receiptAfterPayment() {
            pendingPayment.setStatus(PaymentStatus.PAID);
            pendingPayment.setMethod(PaymentMethod.CASH);
            pendingPayment.setReceiptNumber("RCP-20260924-ABC123");
            pendingPayment.setTransactionReference("TXN-0123456789");
            rideHasPendingPayment();

            ReceiptResponse receipt = paymentService.getReceipt(RIDE_ID, DRIVER_USER_ID, false);

            assertEquals("RCP-20260924-ABC123", receipt.getReceiptNumber());
            assertEquals(new BigDecimal("900.00"), receipt.getTotalAmount());
            assertEquals(PaymentMethod.CASH, receipt.getPaymentMethod());
            assertEquals(ReceiptResponse.FARE_RULE, receipt.getFareRule());
        }

        @Test
        @DisplayName("Admin listing filters by status when given")
        void listPayments() {
            when(paymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(pendingPayment));
            when(paymentRepository.findByStatusOrderByCreatedAtDesc(PaymentStatus.PAID)).thenReturn(List.of());

            assertEquals(1, paymentService.listPayments(null).size());
            assertTrue(paymentService.listPayments(PaymentStatus.PAID).isEmpty());
        }

        @Test
        @DisplayName("A passenger's own payments come back newest first")
        void passengerPayments() {
            when(paymentRepository.findByPassengerIdOrderByCreatedAtDesc(PASSENGER_ID)).thenReturn(List.of(pendingPayment));

            assertEquals(1, paymentService.getPaymentsForPassenger(PASSENGER_ID).size());
        }
    }
}
