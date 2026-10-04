package lk.sliit.ridelink.driver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.sliit.ridelink.driver.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRequest {

    @NotBlank(message = "Vehicle make is required")
    private String make;

    @NotBlank(message = "Vehicle model is required")
    private String model;

    @NotNull(message = "Vehicle year is required")
    @Min(value = 1990, message = "Vehicle year must be 1990 or newer")
    @Max(value = 2030, message = "Vehicle year cannot be in the distant future")
    private Integer year;

    private String color;

    @NotBlank(message = "License plate number is required")
    private String licensePlate;

    @NotNull(message = "Vehicle type is required (e.g. CAR, VAN, SUV, SEDAN, TUK_TUK, BIKE)")
    private VehicleType vehicleType;

    @NotNull(message = "Seating capacity is required")
    @Min(value = 1, message = "Seating capacity must be at least 1")
    @Max(value = 50, message = "Seating capacity cannot exceed 50")
    private Integer seatingCapacity;
}
