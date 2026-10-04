package lk.sliit.ridelink.driver.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverUpdateRequest {

    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    private String phoneNumber;

    private String serviceArea;

    private Double serviceRadiusKm;
}
