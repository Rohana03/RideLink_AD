package lk.sliit.ridelink.account.dto;

import jakarta.validation.constraints.NotNull;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusUpdateRequest {

    @NotNull(message = "Status is required (ACTIVE, SUSPENDED or DEACTIVATED)")
    private AccountStatus status;
}
