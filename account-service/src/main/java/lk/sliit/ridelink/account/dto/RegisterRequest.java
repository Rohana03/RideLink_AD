package lk.sliit.ridelink.account.dto;

import jakarta.validation.constraints.*;
import lk.sliit.ridelink.account.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "Password must contain at least one letter and one digit")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9]{9,15}$", message = "Phone number must be 9 to 15 digits, optionally starting with +")
    private String phoneNumber;

    /** PASSENGER or DRIVER. ADMIN accounts cannot self-register. */
    @NotNull(message = "Role is required (PASSENGER or DRIVER)")
    private Role role;
}
