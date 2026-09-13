package lk.sliit.ridelink.driver.dto;

import lk.sliit.ridelink.driver.entity.Driver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleDriverResponse {

    private Long driverId;
    private String userId;
    private String fullName;
    private String phoneNumber;
    private Double rating;
    private Integer totalTrips;
    private Double currentLatitude;
    private Double currentLongitude;
    private Double distanceKm;
    private String serviceArea;
    private VehicleResponse vehicle;

    public static EligibleDriverResponse fromEntityAndDistance(Driver driver, double distanceKm) {
        if (driver == null) {
            return null;
        }
        return EligibleDriverResponse.builder()
                .driverId(driver.getId())
                .userId(driver.getUserId())
                .fullName(driver.getFullName())
                .phoneNumber(driver.getPhoneNumber())
                .rating(driver.getRating())
                .totalTrips(driver.getTotalTrips())
                .currentLatitude(driver.getCurrentLatitude())
                .currentLongitude(driver.getCurrentLongitude())
                .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
                .serviceArea(driver.getServiceArea())
                .vehicle(VehicleResponse.fromEntity(driver.getVehicle()))
                .build();
    }
}
