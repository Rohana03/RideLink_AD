package lk.sliit.ridelink.driver.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverRegistrationRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Driver license number is required")
    private String licenseNumber;

    @Builder.Default
    private String serviceArea = "Colombo";

    @Builder.Default
    private Double serviceRadiusKm = 15.0;

    @NotNull(message = "Vehicle details are required for driver registration")
    @Valid
    private VehicleRequest vehicle;
}
