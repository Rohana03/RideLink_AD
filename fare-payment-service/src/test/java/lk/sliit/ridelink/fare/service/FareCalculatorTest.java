package lk.sliit.ridelink.fare.service;

import lk.sliit.ridelink.fare.config.FareProperties;
import lk.sliit.ridelink.fare.dto.FareBreakdown;
import lk.sliit.ridelink.fare.dto.FareEstimateRequest;
import lk.sliit.ridelink.fare.entity.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FareCalculatorTest {

    /** Same values as application.yml, so these tests document the real rule. */
    static FareProperties defaultProperties() {
        Map<VehicleType, Double> multipliers = new EnumMap<>(VehicleType.class);
        multipliers.put(VehicleType.BIKE, 0.6);
        multipliers.put(VehicleType.TUK_TUK, 0.8);
        multipliers.put(VehicleType.CAR, 1.0);
        multipliers.put(VehicleType.SEDAN, 1.0);
        multipliers.put(VehicleType.SUV, 1.3);
        multipliers.put(VehicleType.VAN, 1.5);
        return FareProperties.builder()
                .currency("LKR")
                .baseFare(new BigDecimal("100.0"))
                .perKmRate(new BigDecimal("60.0"))
                .perMinRate(new BigDecimal("10.0"))
                .minimumFare(new BigDecimal("200.0"))
                .averageSpeedKmh(30.0)
                .vehicleMultipliers(multipliers)
                .build();
    }

    private final FareCalculator calculator = new FareCalculator(defaultProperties());

    private FareBreakdown measured(VehicleType type, double km, double minutes) {
        return calculator.calculate(6.9271, 79.8612, 6.9000, 79.8500, type, km, minutes);
    }

    @Test
    @DisplayName("CAR, 10 km, 20 min: 100 + 60x10 + 10x20 = 900.00")
    void carFareFollowsRule() {
        FareBreakdown fare = measured(VehicleType.CAR, 10.0, 20.0);

        assertEquals(new BigDecimal("100.00"), fare.getBaseFare());
        assertEquals(new BigDecimal("600.00"), fare.getDistanceCharge());
        assertEquals(new BigDecimal("200.00"), fare.getTimeCharge());
        assertEquals(1.0, fare.getVehicleMultiplier());
        assertEquals(new BigDecimal("900.00"), fare.getTotalFare());
        assertFalse(fare.isMinimumFareApplied());
        assertEquals("LKR", fare.getCurrency());
    }

    @Test
    @DisplayName("Vehicle multiplier scales the whole fare: VAN x1.5 = 1350.00, BIKE x0.6 = 540.00")
    void vehicleMultiplierApplies() {
        assertEquals(new BigDecimal("1350.00"), measured(VehicleType.VAN, 10.0, 20.0).getTotalFare());
        assertEquals(new BigDecimal("540.00"), measured(VehicleType.BIKE, 10.0, 20.0).getTotalFare());
        assertEquals(new BigDecimal("720.00"), measured(VehicleType.TUK_TUK, 10.0, 20.0).getTotalFare());
    }

    @Test
    @DisplayName("A short BIKE trip below the minimum is charged the minimum fare (200.00)")
    void minimumFareApplies() {
        // (100 + 60x0.5 + 10x1) x 0.6 = 84.00 < 200.00
        FareBreakdown fare = measured(VehicleType.BIKE, 0.5, 1.0);

        assertTrue(fare.isMinimumFareApplied());
        assertEquals(new BigDecimal("200.00"), fare.getTotalFare());
    }

    @Test
    @DisplayName("Boundary: a fare exactly equal to the minimum is not flagged as a minimum-fare trip")
    void fareEqualToMinimumIsNotFlagged() {
        // (100 + 60x1 + 10x4) x 1.0 = 200.00
        FareBreakdown fare = measured(VehicleType.CAR, 1.0, 4.0);

        assertFalse(fare.isMinimumFareApplied());
        assertEquals(new BigDecimal("200.00"), fare.getTotalFare());
    }

    @Test
    @DisplayName("Estimate uses Haversine distance and 30 km/h for time; the breakdown is reproducible by hand")
    void estimateUsesHaversineAndAverageSpeed() {
        // Colombo Fort -> Bambalapitiya, roughly 5 km
        FareBreakdown fare = calculator.estimate(FareEstimateRequest.builder()
                .pickupLatitude(6.9344).pickupLongitude(79.8428)
                .dropoffLatitude(6.8905).dropoffLongitude(79.8565)
                .build());

        assertEquals(5.1, fare.getDistanceKm(), 0.2);
        assertEquals(Math.round(fare.getDistanceKm() / 30.0 * 60.0 * 100.0) / 100.0, fare.getDurationMinutes(), 0.001);
        BigDecimal expected = new BigDecimal("100.00")
                .add(new BigDecimal("60").multiply(BigDecimal.valueOf(fare.getDistanceKm())))
                .add(new BigDecimal("10").multiply(BigDecimal.valueOf(fare.getDurationMinutes())))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, fare.getTotalFare());
    }

    @Test
    @DisplayName("No vehicle type means CAR")
    void defaultsToCar() {
        FareBreakdown fare = calculator.calculate(6.9, 79.8, 6.95, 79.85, null, 3.0, 6.0);

        assertEquals(VehicleType.CAR, fare.getVehicleType());
        assertEquals(1.0, fare.getVehicleMultiplier());
    }

    @Test
    @DisplayName("Pickup equal to destination gives 0 km and the minimum fare")
    void zeroDistanceTrip() {
        FareBreakdown fare = calculator.estimate(FareEstimateRequest.builder()
                .pickupLatitude(6.9271).pickupLongitude(79.8612)
                .dropoffLatitude(6.9271).dropoffLongitude(79.8612)
                .vehicleType(VehicleType.SUV)
                .build());

        assertEquals(0.0, fare.getDistanceKm());
        assertEquals(0.0, fare.getDurationMinutes());
        assertTrue(fare.isMinimumFareApplied());
        assertEquals(new BigDecimal("200.00"), fare.getTotalFare());
    }

    @Test
    @DisplayName("A vehicle type missing from the multiplier table is charged at 1.0")
    void missingMultiplierDefaultsToOne() {
        FareProperties properties = defaultProperties();
        properties.getVehicleMultipliers().remove(VehicleType.SUV);

        FareBreakdown fare = new FareCalculator(properties).calculate(0, 0, 0, 0, VehicleType.SUV, 10.0, 20.0);

        assertEquals(1.0, fare.getVehicleMultiplier());
        assertEquals(new BigDecimal("900.00"), fare.getTotalFare());
    }

    @Test
    @DisplayName("Money is rounded half-up to 2 decimals")
    void moneyRoundsHalfUp() {
        // km 1.005 -> 1.01, so 60 x 1.01 = 60.60; minutes 2.345 -> 2.35, so 10 x 2.35 = 23.50
        FareBreakdown fare = measured(VehicleType.CAR, 1.005, 2.345);

        assertEquals(1.01, fare.getDistanceKm());
        assertEquals(2.35, fare.getDurationMinutes());
        assertEquals(new BigDecimal("60.60"), fare.getDistanceCharge());
        assertEquals(new BigDecimal("23.50"), fare.getTimeCharge());
        assertEquals(new BigDecimal("200.00"), fare.getTotalFare());
    }
}
