package lk.sliit.ridelink.account.dto;

import jakarta.validation.constraints.NotNull;
import lk.sliit.ridelink.account.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpdateRequest {

    @NotNull(message = "Role is required (PASSENGER, DRIVER or ADMIN)")
    private Role role;
}
