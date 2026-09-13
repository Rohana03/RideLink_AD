package lk.sliit.ridelink.driver.service;

import lk.sliit.ridelink.driver.dto.*;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.VehicleType;

import java.util.List;

public interface DriverService {

    DriverResponse registerDriver(String userId, DriverRegistrationRequest request);

    DriverResponse getDriverByUserId(String userId);

    DriverResponse getDriverById(Long id);

    DriverResponse updateProfile(String userId, DriverUpdateRequest request);

    DriverResponse updateVehicle(String userId, VehicleRequest request);

    DriverResponse updateAvailability(String userId, DriverAvailabilityStatus status);

    DriverResponse updateLocation(String userId, Double latitude, Double longitude);

    List<EligibleDriverResponse> findEligibleDrivers(
            Double pickupLat,
            Double pickupLng,
            VehicleType vehicleType,
            Double radiusKm,
            Integer limit
    );

    DriverResponse updateInternalStatus(Long driverId, DriverAvailabilityStatus status);

    DriverResponse updateInternalLocation(Long driverId, Double latitude, Double longitude);
}
