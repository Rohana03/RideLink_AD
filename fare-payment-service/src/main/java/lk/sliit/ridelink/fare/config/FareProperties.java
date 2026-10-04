package lk.sliit.ridelink.fare.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lk.sliit.ridelink.fare.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * The documented fare rule's rates, bound from ridelink.fare.* in application.yml
 * so they can be changed without touching code.
 */
@Component
@ConfigurationProperties(prefix = "ridelink.fare")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareProperties {

    @NotBlank
    @Builder.Default
    private String currency = "LKR";

    @NotNull
    @PositiveOrZero
    private BigDecimal baseFare;

    @NotNull
    @PositiveOrZero
    private BigDecimal perKmRate;

    @NotNull
    @PositiveOrZero
    private BigDecimal perMinRate;

    @NotNull
    @PositiveOrZero
    private BigDecimal minimumFare;

    @Positive
    private double averageSpeedKmh;

    /** Vehicle types missing from this map use a multiplier of 1.0. */
    @Builder.Default
    private Map<VehicleType, Double> vehicleMultipliers = new EnumMap<>(VehicleType.class);
}
