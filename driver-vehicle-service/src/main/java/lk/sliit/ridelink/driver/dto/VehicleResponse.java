package lk.sliit.ridelink.driver.dto;

import lk.sliit.ridelink.driver.entity.Vehicle;
import lk.sliit.ridelink.driver.entity.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleResponse {

    private Long id;
    private String make;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;
    private VehicleType vehicleType;
    private Integer seatingCapacity;

    public static VehicleResponse fromEntity(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .year(vehicle.getYear())
                .color(vehicle.getColor())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleType(vehicle.getVehicleType())
                .seatingCapacity(vehicle.getSeatingCapacity())
                .build();
    }
}
