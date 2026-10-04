package lk.sliit.ridelink.fare.dto;

import lk.sliit.ridelink.fare.entity.Payment;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Receipt for a PAID ride. Built from the payment record, never stored separately. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptResponse {

    public static final String FARE_RULE =
            "max(minimumFare, (baseFare + perKmRate x km + perMinRate x minutes) x vehicleMultiplier)";

    private String receiptNumber;
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
    private PaymentMethod paymentMethod;
    private String cardLast4;
    private String transactionReference;
    private LocalDateTime paidAt;
    private String fareRule;

    public static ReceiptResponse fromEntity(Payment payment) {
        return ReceiptResponse.builder()
                .receiptNumber(payment.getReceiptNumber())
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
                .paymentMethod(payment.getMethod())
                .cardLast4(payment.getCardLast4())
                .transactionReference(payment.getTransactionReference())
                .paidAt(payment.getPaidAt())
                .fareRule(FARE_RULE)
                .build();
    }
}
