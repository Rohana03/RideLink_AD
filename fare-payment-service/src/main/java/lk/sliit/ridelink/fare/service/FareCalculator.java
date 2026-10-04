package lk.sliit.ridelink.fare.service;

import lk.sliit.ridelink.fare.config.FareProperties;
import lk.sliit.ridelink.fare.dto.FareBreakdown;
import lk.sliit.ridelink.fare.dto.FareEstimateRequest;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The documented fare rule, used for both the estimate and the final fare:
 * <pre>
 *   km      = measured distance, or the Haversine distance between pickup and destination
 *   minutes = measured duration, or km / averageSpeedKmh * 60
 *   fare    = max(minimumFare, (baseFare + perKmRate * km + perMinRate * minutes) * vehicleMultiplier)
 * </pre>
 * km and minutes are rounded to 2 decimals first, so every line of the breakdown can be
 * reproduced by hand from the values shown. Money is rounded half-up to 2 decimals.
 */
@Component
@RequiredArgsConstructor
public class FareCalculator {

    public static final VehicleType DEFAULT_VEHICLE_TYPE = VehicleType.CAR;

    private final FareProperties properties;

    public FareBreakdown estimate(FareEstimateRequest request) {
        return calculate(request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDropoffLatitude(), request.getDropoffLongitude(),
                request.getVehicleType(), null, null);
    }

    /**
     * @param actualDistanceKm      measured distance, or null to use the straight-line distance
     * @param actualDurationMinutes measured duration, or null to estimate it from the distance
     */
    public FareBreakdown calculate(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng,
                                   VehicleType vehicleType, Double actualDistanceKm, Double actualDurationMinutes) {
        VehicleType type = vehicleType != null ? vehicleType : DEFAULT_VEHICLE_TYPE;

        double km = round2(actualDistanceKm != null
                ? actualDistanceKm
                : GeoUtils.calculateDistanceKm(pickupLat, pickupLng, dropoffLat, dropoffLng));
        double minutes = round2(actualDurationMinutes != null
                ? actualDurationMinutes
                : km / properties.getAverageSpeedKmh() * 60.0);
        double multiplier = properties.getVehicleMultipliers().getOrDefault(type, 1.0);

        BigDecimal baseFare = money(properties.getBaseFare());
        BigDecimal distanceCharge = money(properties.getPerKmRate().multiply(BigDecimal.valueOf(km)));
        BigDecimal timeCharge = money(properties.getPerMinRate().multiply(BigDecimal.valueOf(minutes)));
        BigDecimal subtotal = money(baseFare.add(distanceCharge).add(timeCharge)
                .multiply(BigDecimal.valueOf(multiplier)));

        BigDecimal minimumFare = money(properties.getMinimumFare());
        boolean minimumApplied = subtotal.compareTo(minimumFare) < 0;

        return FareBreakdown.builder()
                .distanceKm(km)
                .durationMinutes(minutes)
                .vehicleType(type)
                .baseFare(baseFare)
                .distanceCharge(distanceCharge)
                .timeCharge(timeCharge)
                .vehicleMultiplier(multiplier)
                .minimumFareApplied(minimumApplied)
                .totalFare(minimumApplied ? minimumFare : subtotal)
                .currency(properties.getCurrency())
                .build();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
