package lk.sliit.ridelink.driver.dto;

import jakarta.validation.constraints.NotNull;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private DriverAvailabilityStatus status;
}
