package lk.sliit.ridelink.fare.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One payment per completed ride. Created when the Ride Management Service reports a
 * completed ride, then updated by the passenger's simulated payment attempts.
 * Amounts are stored as Decimal128 so no floating-point rounding creeps in.
 */
@Document(collection = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    private String id;

    /** Optimistic lock: two concurrent payment attempts cannot both succeed. */
    @Version
    private Long version;

    /** Ride ID from the Ride Management Service; unique, so a ride is never charged twice. */
    @Indexed(unique = true)
    private String rideId;

    /** Account userId of the passenger; only they may pay. */
    @Indexed
    private String passengerId;

    /** Driver & Vehicle Service driver ID. */
    private String driverId;

    /** Account userId of the driver, so the driver can view the receipt. */
    private String driverUserId;

    private VehicleType vehicleType;

    private double distanceKm;

    private double durationMinutes;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal baseFare;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal distanceCharge;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal timeCharge;

    private double vehicleMultiplier;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;

    private String currency;

    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    private PaymentMethod method;

    /** Only the last four digits are kept; the full card number is never stored. */
    private String cardLast4;

    @Builder.Default
    private int attempts = 0;

    private String failureReason;

    private String transactionReference;

    private String receiptNumber;

    private LocalDateTime rideCompletedAt;

    private LocalDateTime paidAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
