package lk.sliit.ridelink.driver.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    private String make;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;

    @Builder.Default
    private VehicleType vehicleType = VehicleType.CAR;

    @Builder.Default
    private Integer seatingCapacity = 4;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
