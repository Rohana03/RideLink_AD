package lk.sliit.ridelink.fare.dto;

import lk.sliit.ridelink.fare.entity.Payment;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String id;
    private String rideId;
    private String passengerId;
    private String driverId;
    private VehicleType vehicleType;
    private double distanceKm;
    private double durationMinutes;
    private BigDecimal baseFare;
    private BigDecimal distanceCharge;
    private BigDecimal timeCharge;
    private double vehicleMultiplier;
    private BigDecimal totalAmount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod method;
    private String cardLast4;
    private int attempts;
    private String failureReason;
    private String transactionReference;
    private String receiptNumber;
    private LocalDateTime rideCompletedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse fromEntity(Payment payment) {
        if (payment == null) {
            return null;
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .rideId(payment.getRideId())
                .passengerId(payment.getPassengerId())
                .driverId(payment.getDriverId())
                .vehicleType(payment.getVehicleType())
                .distanceKm(payment.getDistanceKm())
                .durationMinutes(payment.getDurationMinutes())
                .baseFare(payment.getBaseFare())
                .distanceCharge(payment.getDistanceCharge())
                .timeCharge(payment.getTimeCharge())
                .vehicleMultiplier(payment.getVehicleMultiplier())
                .totalAmount(payment.getTotalAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .cardLast4(payment.getCardLast4())
                .attempts(payment.getAttempts())
                .failureReason(payment.getFailureReason())
                .transactionReference(payment.getTransactionReference())
                .receiptNumber(payment.getReceiptNumber())
                .rideCompletedAt(payment.getRideCompletedAt())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
