package lk.sliit.ridelink.ride.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields Ride Management keeps from Fare &amp; Payment's estimate response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FareEstimate(
        Double distanceKm,
        Double durationMinutes,
        BigDecimal totalFare,
        String currency
) {
}
