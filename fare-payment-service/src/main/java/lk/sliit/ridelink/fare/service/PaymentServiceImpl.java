package lk.sliit.ridelink.fare.service;

import lk.sliit.ridelink.fare.dto.*;
import lk.sliit.ridelink.fare.entity.Payment;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.exception.*;
import lk.sliit.ridelink.fare.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    /** Simulation rule: a card number ending in this suffix is declined. */
    static final String DECLINED_CARD_SUFFIX = "0000";

    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentRepository paymentRepository;
    private final FareCalculator fareCalculator;

    @Override
    public PaymentResponse recordCompletedRide(RideCompletedEvent event) {
        Optional<Payment> existing = paymentRepository.findByRideId(event.getRideId());
        if (existing.isPresent()) {
            log.info("Ride {} already recorded; ignoring duplicate completion event", event.getRideId());
            return PaymentResponse.fromEntity(existing.get());
        }

        FareBreakdown fare = fareCalculator.calculate(
                event.getPickupLatitude(), event.getPickupLongitude(),
                event.getDropoffLatitude(), event.getDropoffLongitude(),
                event.getVehicleType(), event.getActualDistanceKm(), event.getActualDurationMinutes());

        Payment payment = Payment.builder()
                .rideId(event.getRideId())
                .passengerId(event.getPassengerId())
                .driverId(event.getDriverId())
                .driverUserId(event.getDriverUserId())
                .vehicleType(fare.getVehicleType())
                .distanceKm(fare.getDistanceKm())
                .durationMinutes(fare.getDurationMinutes())
                .baseFare(fare.getBaseFare())
                .distanceCharge(fare.getDistanceCharge())
                .timeCharge(fare.getTimeCharge())
                .vehicleMultiplier(fare.getVehicleMultiplier())
                .totalAmount(fare.getTotalFare())
                .currency(fare.getCurrency())
                .status(PaymentStatus.PENDING)
                .rideCompletedAt(event.getCompletedAt() != null ? event.getCompletedAt() : LocalDateTime.now())
                .build();

        try {
            Payment saved = paymentRepository.save(payment);
            log.info("Ride {} completed: final fare {} {} (PENDING)", saved.getRideId(),
                    saved.getTotalAmount(), saved.getCurrency());
            return PaymentResponse.fromEntity(saved);
        } catch (DuplicateKeyException e) {
            // The same event was delivered twice at the same moment; the unique rideId index kept one.
            return paymentRepository.findByRideId(event.getRideId())
                    .map(PaymentResponse::fromEntity)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public PaymentResponse pay(String rideId, String passengerId, PaymentRequest request) {
        Payment payment = findPayment(rideId);

        if (!payment.getPassengerId().equals(passengerId)) {
            throw new ForbiddenOperationException("Only the passenger of this ride can pay for it");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new InvalidPaymentStateException(
                    "Ride " + rideId + " is already paid (receipt " + payment.getReceiptNumber() + ")");
        }
        if (request.getMethod() == PaymentMethod.CARD && request.getCardNumber() == null) {
            throw new BadRequestException("Card number is required for CARD payments");
        }

        payment.setAttempts(payment.getAttempts() + 1);
        payment.setMethod(request.getMethod());
        payment.setCardLast4(request.getMethod() == PaymentMethod.CARD ? last4(request.getCardNumber()) : null);
        payment.setUpdatedAt(LocalDateTime.now());

        if (isDeclined(request)) {
            String reason = "Card declined (simulated: card numbers ending in " + DECLINED_CARD_SUFFIX + " are declined)";
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            paymentRepository.save(payment);
            log.info("Payment for ride {} declined (attempt {})", rideId, payment.getAttempts());
            throw new PaymentDeclinedException(reason + ". Retry with another card or method.");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.PAID);
        payment.setFailureReason(null);
        payment.setTransactionReference("TXN-" + randomCode(10));
        payment.setReceiptNumber("RCP-" + now.format(RECEIPT_DATE) + "-" + randomCode(6));
        payment.setPaidAt(now);

        Payment saved = paymentRepository.save(payment);
        log.info("Payment for ride {} succeeded: {} {} via {}", rideId, saved.getTotalAmount(),
                saved.getCurrency(), saved.getMethod());
        return PaymentResponse.fromEntity(saved);
    }

    @Override
    public PaymentResponse getPayment(String rideId, String callerId, boolean callerIsAdmin) {
        Payment payment = findPayment(rideId);
        ensureCanView(payment, callerId, callerIsAdmin);
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    public List<PaymentResponse> getPaymentsForPassenger(String passengerId) {
        return paymentRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
                .map(PaymentResponse::fromEntity)
                .toList();
    }

    @Override
    public List<PaymentResponse> listPayments(PaymentStatus status) {
        List<Payment> payments = status != null
                ? paymentRepository.findByStatusOrderByCreatedAtDesc(status)
                : paymentRepository.findAllByOrderByCreatedAtDesc();
        return payments.stream().map(PaymentResponse::fromEntity).toList();
    }

    @Override
    public ReceiptResponse getReceipt(String rideId, String callerId, boolean callerIsAdmin) {
        Payment payment = findPayment(rideId);
        ensureCanView(payment, callerId, callerIsAdmin);
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new InvalidPaymentStateException(
                    "Receipt is available only after payment is completed (current status: " + payment.getStatus() + ")");
        }
        return ReceiptResponse.fromEntity(payment);
    }

    private Payment findPayment(String rideId) {
        return paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("No completed ride found for payment with ride ID: " + rideId));
    }

    private void ensureCanView(Payment payment, String callerId, boolean callerIsAdmin) {
        boolean involved = callerId != null
                && (callerId.equals(payment.getPassengerId()) || callerId.equals(payment.getDriverUserId()));
        if (!callerIsAdmin && !involved) {
            throw new ForbiddenOperationException("You can only view payments for your own rides");
        }
    }

    private static boolean isDeclined(PaymentRequest request) {
        return request.getMethod() == PaymentMethod.CARD && request.getCardNumber().endsWith(DECLINED_CARD_SUFFIX);
    }

    private static String last4(String cardNumber) {
        return cardNumber.substring(cardNumber.length() - 4);
    }

    private static String randomCode(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase(Locale.ROOT);
    }
}
