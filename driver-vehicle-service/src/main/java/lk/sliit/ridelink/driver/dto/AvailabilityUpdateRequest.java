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
public class AvailabilityUpdateRequest {

    @NotNull(message = "Availability status is required (AVAILABLE or OFFLINE)")
    private DriverAvailabilityStatus availabilityStatus;
}
