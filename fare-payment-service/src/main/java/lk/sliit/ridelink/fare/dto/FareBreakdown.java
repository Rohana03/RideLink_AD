package lk.sliit.ridelink.fare.dto;

import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Result of the fare rule, returned as an estimate and reused for the final fare. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareBreakdown {

    private double distanceKm;
    private double durationMinutes;
    private VehicleType vehicleType;
    private BigDecimal baseFare;
    private BigDecimal distanceCharge;
    private BigDecimal timeCharge;
    private double vehicleMultiplier;
    /** True when the formula came out below the minimum fare and the minimum was charged instead. */
    private boolean minimumFareApplied;
    private BigDecimal totalFare;
    private String currency;
}
