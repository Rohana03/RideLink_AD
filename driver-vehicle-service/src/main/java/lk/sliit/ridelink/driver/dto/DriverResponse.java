package lk.sliit.ridelink.driver.dto;

import lk.sliit.ridelink.driver.entity.Driver;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.OperationalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverResponse {

    private Long id;
    private String userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String licenseNumber;
    private String serviceArea;
    private Double serviceRadiusKm;
    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime lastLocationUpdate;
    private DriverAvailabilityStatus availabilityStatus;
    private OperationalStatus operationalStatus;
    private Double rating;
    private Integer totalTrips;
    private VehicleResponse vehicle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DriverResponse fromEntity(Driver driver) {
        if (driver == null) {
            return null;
        }
        return DriverResponse.builder()
                .id(driver.getId())
                .userId(driver.getUserId())
                .fullName(driver.getFullName())
                .email(driver.getEmail())
                .phoneNumber(driver.getPhoneNumber())
                .licenseNumber(driver.getLicenseNumber())
                .serviceArea(driver.getServiceArea())
                .serviceRadiusKm(driver.getServiceRadiusKm())
                .currentLatitude(driver.getCurrentLatitude())
                .currentLongitude(driver.getCurrentLongitude())
                .lastLocationUpdate(driver.getLastLocationUpdate())
                .availabilityStatus(driver.getAvailabilityStatus())
                .operationalStatus(driver.getOperationalStatus())
                .rating(driver.getRating())
                .totalTrips(driver.getTotalTrips())
                .vehicle(VehicleResponse.fromEntity(driver.getVehicle()))
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }
}
