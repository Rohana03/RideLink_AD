package lk.sliit.ridelink.fare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lk.sliit.ridelink.fare.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Payment method is required (CARD, CASH or WALLET)")
    private PaymentMethod method;

    /**
     * Required for CARD only. Simulated: a number ending in 0000 is declined, any other is approved.
     * Use fictional numbers only; just the last four digits are stored.
     */
    @Pattern(regexp = "^[0-9]{12,19}$", message = "Card number must be 12 to 19 digits")
    private String cardNumber;
}
