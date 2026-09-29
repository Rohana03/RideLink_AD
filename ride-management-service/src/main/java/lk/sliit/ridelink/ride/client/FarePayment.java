package lk.sliit.ridelink.ride.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields Ride Management keeps from the payment Fare &amp; Payment opens for a completed ride. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FarePayment(
        String rideId,
        BigDecimal totalAmount,
        String currency,
        String status
) {
}
